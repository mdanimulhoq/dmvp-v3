/**
 * @file src/middleware/signatureVerify.js
 * @description Device-signature verification and replay-protection
 * middleware for the DMVP v3.0 backend API.
 *
 * This module verifies that critical write requests (evidence
 * registration, device lifecycle transitions, etc.) were actually signed
 * by the private key held in the requesting device's secure hardware
 * boundary, per TDD v3.0 sections 5.3 and 6.3, and SRS FR-CR-04/FR-CR-05.
 *
 * Responsibilities:
 *  - deterministic canonical serialization of the signed payload
 *    (stable key ordering, so client and server always sign/verify the
 *    same bytes regardless of JSON key order on the wire)
 *  - ECDSA (SHA256withECDSA / P-256) signature verification against the
 *    device's registered public key
 *  - nonce-bound replay protection with a bounded expiration window
 *  - device trust-state checks (must not be REVOKED) before accepting
 *    a signed write
 *
 * This module is intentionally separate from `src/middleware/auth.js`:
 * `auth.js` answers "which account is calling", while this module
 * answers "did the claimed device really sign this exact payload,
 * exactly once".
 *
 * All failures use the DMVP structured error envelope:
 *
 *   {
 *     error_code: string,
 *     message: string,
 *     detail: object,
 *     policy_version: string,
 *     request_id: string
 *   }
 */

'use strict';

const crypto = require('crypto');
const { prisma } = require('../config/database');
const { verifyEcdsaSha256Signature } = require('../utils/cryptoUtils');

/** Header carrying the Base64-encoded request signature. */
const SIGNATURE_HEADER = 'x-dmvp-signature';

/** Header carrying the single-use nonce bound to this signed request. */
const NONCE_HEADER = 'x-dmvp-nonce';

/** Header carrying the ISO-8601 timestamp the client signed against. */
const TIMESTAMP_HEADER = 'x-dmvp-timestamp';

/** Header carrying the device key identifier that produced the signature. */
const DEVICE_KEY_ID_HEADER = 'x-dmvp-device-key-id';

/** Legacy header names for backward compatibility */
const LEGACY_SIGNATURE_HEADER = 'x-request-signature';
const LEGACY_NONCE_HEADER = 'x-nonce';
const LEGACY_TIMESTAMP_HEADER = 'x-timestamp';
const LEGACY_DEVICE_KEY_ID_HEADER = 'x-dmvp-key-id';

/**
 * Maximum allowed clock skew between the client-claimed signing
 * timestamp and server time, in either direction. Requests outside this
 * window are rejected as stale or as potential replay/clock-forgery
 * attempts, per SRS NFR-SC-02.
 */
const REPLAY_WINDOW_MS = 5 * 60 * 1000; // 5 minutes

/**
 * In-memory nonce cache used for replay protection.
 *
 * Maps `${deviceKeyId}:${nonce}` -> expiry epoch millis. Entries are
 * swept lazily on each check and periodically via `setInterval`.
 *
 * NOTE: this in-memory store is correct for a single backend instance.
 * A horizontally-scaled deployment must replace this with a shared
 * store (e.g. Redis `SET key val NX PX <window>`) that provides atomic
 * "insert if absent" semantics across instances. The public interface
 * (`checkAndConsumeNonce`) is isolated below specifically to make that
 * swap a one-function change.
 *
 * @type {Map<string, number>}
 */
const nonceCache = new Map();

/** Periodic sweep interval for expired nonce cache entries. */
const NONCE_SWEEP_INTERVAL_MS = 60 * 1000;

setInterval(() => {
  const now = Date.now();
  for (const [key, expiresAt] of nonceCache.entries()) {
    if (expiresAt <= now) {
      nonceCache.delete(key);
    }
  }
}, NONCE_SWEEP_INTERVAL_MS).unref();

/**
 * Atomically check whether a nonce has already been consumed for a
 * given device, and if not, record it as consumed for the duration of
 * the replay window.
 *
 * @param {string} deviceKeyId
 * @param {string} nonce
 * @returns {boolean} true if the nonce was fresh and is now consumed;
 *   false if the nonce was already seen (i.e. a replay).
 */
function checkAndConsumeNonce(deviceKeyId, nonce) {
  const key = `${deviceKeyId}:${nonce}`;
  const now = Date.now();

  const existingExpiry = nonceCache.get(key);
  if (existingExpiry && existingExpiry > now) {
    return false;
  }

  nonceCache.set(key, now + REPLAY_WINDOW_MS);
  return true;
}

/* ------------------------------------------------------------------ *
 * Shared helpers (mirrors conventions in rateLimit.js / validation.js /
 * auth.js)
 * ------------------------------------------------------------------ */

/**
 * Resolve the currently active verification policy version.
 *
 * @returns {string} policy version identifier
 */
function getPolicyVersion() {
  return process.env.VERIFICATION_POLICY_VERSION || 'unspecified';
}

/**
 * Generate or forward a request identifier for correlation in logs,
 * audit trails, and support requests.
 *
 * @param {import('express').Request} req
 * @returns {string} request identifier
 */
function resolveRequestId(req) {
  const incoming = req.headers['x-request-id'];
  if (typeof incoming === 'string' && incoming.trim().length > 0) {
    return incoming.trim();
  }
  return crypto.randomUUID();
}

/**
 * Send a structured signature-verification error response.
 *
 * @param {import('express').Response} res
 * @param {import('express').Request} req
 * @param {number} statusCode
 * @param {string} errorCode
 * @param {string} message
 * @param {object} [detail]
 */
function sendSignatureError(
  res,
  req,
  statusCode,
  errorCode,
  message,
  detail = {}
) {
  res.status(statusCode).json({
    error_code: errorCode,
    message,
    detail,
    policy_version: getPolicyVersion(),
    request_id: resolveRequestId(req),
  });
}

/**
 * Read a header from the request, supporting multiple header names.
 *
 * @param {import('express').Request} req
 * @param {string[]} names - Header names to try in order
 * @returns {string|null} Header value or null if not found
 */
function readHeader(req, names) {
  for (const name of names) {
    const value = req.headers[name];
    if (typeof value === 'string' && value.trim().length > 0) {
      return value.trim();
    }
  }
  return null;
}

/* ------------------------------------------------------------------ *
 * Canonical serialization
 * ------------------------------------------------------------------ */

/**
 * Recursively sort object keys and rebuild arrays/objects so that
 * `JSON.stringify` of the result is byte-stable regardless of the
 * original key insertion order. Matches the "canonical JSON" profile
 * referenced in TDD v3.0 section 5.3.
 *
 * @param {*} value
 * @returns {*} a structurally identical value with deterministic key order
 */
function canonicalizeValue(value) {
  if (Array.isArray(value)) {
    return value.map(canonicalizeValue);
  }

  if (value !== null && typeof value === 'object') {
    const sortedKeys = Object.keys(value).sort();
    const result = {};
    for (const key of sortedKeys) {
      result[key] = canonicalizeValue(value[key]);
    }
    return result;
  }

  return value;
}

/**
 * Produce the canonical, deterministic byte representation of a payload
 * that clients sign and the server verifies against.
 *
 * The `signature` field itself (if present) is always excluded, since
 * a signature cannot cover itself.
 *
 * @param {object} payload - The evidence envelope or request body to
 *   canonicalize.
 * @returns {Buffer} UTF-8 encoded canonical JSON bytes
 */
function canonicalSerialize(payload) {
  const { signature, ...unsigned } = payload || {};
  const canonical = canonicalizeValue(unsigned);
  return Buffer.from(JSON.stringify(canonical), 'utf8');
}

/* ------------------------------------------------------------------ *
 * Signature verification
 * ------------------------------------------------------------------ */

/**
 * Verify a Base64-encoded ECDSA (SHA-256 / P-256) signature over the
 * canonical bytes of a payload, using the device's registered public
 * key (SPKI PEM format).
 *
 * @param {Buffer} canonicalBytes - Output of `canonicalSerialize`.
 * @param {string} signatureBase64 - Base64-encoded DER signature.
 * @param {string} publicKeyPem - Device public key in SPKI PEM format.
 * @returns {boolean} true if the signature is valid for the given payload
 */
function verifyEcdsaSignature(canonicalBytes, signatureBase64, publicKeyPem) {
  return verifyEcdsaSha256Signature(
    canonicalBytes,
    signatureBase64,
    publicKeyPem
  );
}

/**
 * Validate that a claimed signing timestamp falls within the allowed
 * replay window relative to server time.
 *
 * @param {string} timestampHeaderValue - ISO-8601 timestamp string.
 * @returns {{ valid: boolean, reason?: string }}
 */
function validateTimestamp(timestampHeaderValue) {
  const claimedMs = Date.parse(timestampHeaderValue);

  if (Number.isNaN(claimedMs)) {
    return { valid: false, reason: 'not a valid ISO-8601 timestamp' };
  }

  const nowMs = Date.now();
  const skew = Math.abs(nowMs - claimedMs);

  if (skew > REPLAY_WINDOW_MS) {
    return {
      valid: false,
      reason: `timestamp is outside the ${
        REPLAY_WINDOW_MS / 1000
      }-second replay window`,
    };
  }

  return { valid: true };
}

/**
 * Load the device's current key record and verify its trust state
 * permits accepting new signed writes.
 *
 * @param {string} deviceKeyId
 * @returns {Promise<{ ok: true, device: object } | { ok: false, errorCode: string, message: string }>}
 */
async function loadTrustedDevice(deviceKeyId) {
  const device = await prisma.device.findUnique({
    where: { keyId: deviceKeyId },
  });

  if (!device) {
    return {
      ok: false,
      errorCode: 'DEVICE_NOT_FOUND',
      message: 'No registered device matches the claimed device key id.',
    };
  }

  if (device.trustTier === 'TIER_D' || device.revokedAt) {
    return {
      ok: false,
      errorCode: 'DEVICE_REVOKED',
      message: 'This device key has been revoked and cannot sign new writes.',
    };
  }

  return { ok: true, device };
}

/**
 * Express middleware factory that verifies device signatures and
 * enforces nonce-bound replay protection on critical write requests.
 *
 * Expects the following request headers:
 *  - `x-dmvp-signature`      Base64-encoded ECDSA signature
 *  - `x-dmvp-nonce`          Single-use nonce bound to this request
 *  - `x-dmvp-timestamp`      ISO-8601 timestamp the client signed against
 *  - `x-dmvp-device-key-id`  Identifier of the signing device key
 *
 * The request body (minus any `signature` field) is canonically
 * serialized and verified against the device's registered public key.
 *
 * On success, attaches `req.verifiedDevice = { deviceKeyId, trustTier, ... }`
 * and calls `next()`. On failure, responds with a structured error and
 * does not call `next()`.
 *
 * @param {import('express').Request} req
 * @param {import('express').Response} res
 * @param {import('express').NextFunction} next
 */
async function verifySignature(req, res, next) {
  const signatureBase64 = readHeader(req, [
    SIGNATURE_HEADER,
    LEGACY_SIGNATURE_HEADER,
  ]);

  const nonce = readHeader(req, [NONCE_HEADER, LEGACY_NONCE_HEADER]);

  const timestampHeaderValue = readHeader(req, [
    TIMESTAMP_HEADER,
    LEGACY_TIMESTAMP_HEADER,
  ]);

  const deviceKeyId = readHeader(req, [
    DEVICE_KEY_ID_HEADER,
    LEGACY_DEVICE_KEY_ID_HEADER,
  ]) || req.body?.signer_device_key_id || req.body?.signerDeviceKeyId;

  const missing = [];
  if (!signatureBase64) missing.push(SIGNATURE_HEADER);
  if (!nonce) missing.push(NONCE_HEADER);
  if (!timestampHeaderValue) missing.push(TIMESTAMP_HEADER);
  if (!deviceKeyId) missing.push(DEVICE_KEY_ID_HEADER);

  if (missing.length > 0) {
    sendSignatureError(
      res,
      req,
      400,
      'SIGNATURE_HEADERS_MISSING',
      'One or more required signature headers are missing from the request.',
      { missing_headers: missing }
    );
    return;
  }

  const timestampCheck = validateTimestamp(timestampHeaderValue);
  if (!timestampCheck.valid) {
    sendSignatureError(
      res,
      req,
      401,
      'SIGNATURE_TIMESTAMP_INVALID',
      'The signed request timestamp is invalid or outside the allowed window.',
      { reason: timestampCheck.reason }
    );
    return;
  }

  const nonceIsFresh = checkAndConsumeNonce(deviceKeyId, nonce);
  if (!nonceIsFresh) {
    sendSignatureError(
      res,
      req,
      401,
      'REPLAY_DETECTED',
      'This request nonce has already been used and cannot be replayed.'
    );
    return;
  }

  let deviceLookup;
  try {
    deviceLookup = await loadTrustedDevice(deviceKeyId);
  } catch (err) {
    sendSignatureError(
      res,
      req,
      500,
      'SIGNATURE_VERIFICATION_FAILED',
      'Device trust state could not be evaluated due to an internal error.'
    );
    return;
  }

  if (!deviceLookup.ok) {
    const statusCode =
      deviceLookup.errorCode === 'DEVICE_NOT_FOUND' ? 404 : 403;
    sendSignatureError(
      res,
      req,
      statusCode,
      deviceLookup.errorCode,
      deviceLookup.message
    );
    return;
  }

  const { device } = deviceLookup;
  const canonicalBytes = canonicalSerialize(req.body);
  const signatureValid = verifyEcdsaSignature(
    canonicalBytes,
    signatureBase64,
    device.publicKeyPem
  );

  if (!signatureValid) {
    sendSignatureError(
      res,
      req,
      401,
      'INVALID_SIGNATURE',
      'The request signature does not match the signed payload for the claimed device key.'
    );
    return;
  }

  req.verifiedDevice = {
    deviceId: device.deviceId,
    deviceKeyId: device.keyId,
    trustTier: device.trustTier,
    hardwareBacked: device.hardwareBacked,
    revokedAt: device.revokedAt,
  };

  next();
}

module.exports = {
  verifySignature,
  canonicalSerialize,
  verifyEcdsaSignature,
  checkAndConsumeNonce,
  REPLAY_WINDOW_MS,
};
