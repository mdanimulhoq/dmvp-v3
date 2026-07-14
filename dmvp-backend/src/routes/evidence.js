/**
 * @file src/routes/evidence.js
 * @description DMVP v3.0 — Evidence Registration Routes
 *
 * Endpoints:
 *   POST /evidence              — Register new Canonical Evidence Envelope
 *   GET  /evidence/:evidenceId  — Fetch evidence record
 *   GET  /evidence/by-hash/:sha256 — Exact hash lookup
 *
 * @module routes/evidence
 * @version dmvp-v3.0.0
 */

'use strict';

const express = require('express');
const router = express.Router();

const { registerRateLimit } = require('../middleware/rateLimit');
const { prisma } = require('../config/database');
const { computeSHA256Sync } = require('../utils/hashUtils');

// ── Step 3.2: Import idempotency utilities ─────────────────────────────────
const {
  getIdempotencyKey,
  isValidIdempotencyKey,
  computeRequestHash,
  claimIdempotencyKey,
  completeIdempotencyKey,
} = require('../utils/idempotency');

// ── Step 3.3: Import signature verification middleware ─────────────────────
const { verifySignature } = require('../middleware/signatureVerify');

const POLICY_VERSION = process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0';
const PROTOCOL_VERSION = process.env.DMVP_PROTOCOL_VERSION || 'dmvp-v3.0.0';

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

function buildError(status, errorCode, message, detail = null, req = null) {
  return {
    status,
    errorCode,
    message,
    detail,
    policy_version: POLICY_VERSION,
    request_id: req?.requestId || 'unknown',
  };
}

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function isValidUUID(str) {
  return typeof str === 'string' && UUID_REGEX.test(str);
}

function isValidSHA256(str) {
  return typeof str === 'string' && /^[0-9a-f]{64}$/i.test(str);
}

// ── Step 3.3: Helper to pick snake_case or camelCase fields ────────────────

function pick(body, snakeName, camelName, fallback = null) {
  if (body && body[snakeName] !== undefined) {
    return body[snakeName];
  }

  if (body && body[camelName] !== undefined) {
    return body[camelName];
  }

  return fallback;
}

// ── Step 3.3: Build evidence response with data envelope ──────────────────

function buildEvidenceResponse(evidence, idempotencyKey, warnings = []) {
  return {
    data: {
      evidence_id: evidence.evidenceId,
      media_type: evidence.mediaType,
      sha256_original: evidence.sha256Original,
      canonical_media_hash: evidence.canonicalMediaHash,
      fingerprint_profile: evidence.robustFingerprintProfile,
      fingerprint_algorithm_versions: evidence.fingerprintAlgorithmVersions,
      signer_device_key_id: evidence.signerDeviceKeyId,
      timestamp_references: {
        registration_server_time: evidence.registrationServerTime.toISOString(),
        trusted_timestamp_token_reference: evidence.trustedTimestampTokenReference,
      },
      privacy_flags: evidence.privacyFlags,
      lifecycle_state: evidence.status,
      created_at: evidence.createdAt.toISOString(),
      updated_at: evidence.updatedAt.toISOString(),
    },
    server_time: new Date().toISOString(),
    warnings,
    idempotencyKey,
    protocol_version: PROTOCOL_VERSION,
    policy_version: POLICY_VERSION,
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// POST /evidence
// Register a new Canonical Evidence Envelope (CEE)
// ─────────────────────────────────────────────────────────────────────────────

// ── Step 3.3: Remove authenticate, add verifySignature ──────────────────

router.post(
  '/',
  registerRateLimit,
  verifySignature,
  async (req, res, next) => {
    try {
      // ── Step 3.3: Use pick() for snake_case support ─────────────────────
      const body = req.body || {};

      const evidenceId = pick(body, 'evidence_id', 'evidenceId');
      const mediaType = pick(body, 'media_type', 'mediaType');
      const sha256Original = pick(body, 'sha256_original', 'sha256Original');
      const canonicalMediaHash = pick(body, 'canonical_media_hash', 'canonicalMediaHash');
      const robustFingerprintProfile = pick(body, 'robust_fingerprint_profile', 'robustFingerprintProfile');
      const fingerprintAlgorithmVersions = pick(body, 'fingerprint_algorithm_versions', 'fingerprintAlgorithmVersions');
      const signerDeviceKeyId = pick(body, 'signer_device_key_id', 'signerDeviceKeyId');
      const signerPublicKeyReference = pick(body, 'signer_public_key_reference', 'signerPublicKeyReference');
      const signatureAlgorithm = pick(body, 'signature_algorithm', 'signatureAlgorithm');
      const deviceAttestationSummary = pick(body, 'device_attestation_summary', 'deviceAttestationSummary');
      const trustedTimestampTokenReference = pick(body, 'trusted_timestamp_token_reference', 'trustedTimestampTokenReference');
      const captureTimeClaim = pick(body, 'capture_time_claim', 'captureTimeClaim');
      const geolocationClaim = pick(body, 'geolocation_claim', 'geolocationClaim');
      const privacyFlags = pick(body, 'privacy_flags', 'privacyFlags');
      const clientAppVersion = pick(body, 'client_app_version', 'clientAppVersion');
      const verificationPolicyVersion = pick(body, 'verification_policy_version', 'verificationPolicyVersion');
      const chainParentEvidenceId = pick(body, 'chain_parent_evidence_id', 'chainParentEvidenceId');
      const auditReference = pick(body, 'audit_reference', 'auditReference');
      const signature = pick(body, 'signature', 'signature');
      const perceptualHash = pick(body, 'perceptual_hash', 'perceptualHash');
      const fingerprintPrimary = pick(body, 'fingerprint_primary', 'fingerprintPrimary');
      const fingerprintSearchTokens = pick(body, 'fingerprint_search_tokens', 'fingerprintSearchTokens');
      const durationMs = pick(body, 'duration_ms', 'durationMs');
      const width = pick(body, 'width', 'width');
      const height = pick(body, 'height', 'height');
      const codec = pick(body, 'codec', 'codec');
      const frameRate = pick(body, 'frame_rate', 'frameRate');
      const transformHints = pick(body, 'transform_hints', 'transformHints');

      const geolocationLat = pick(body, 'geolocation_lat', 'geolocationLat', geolocationClaim?.lat);
      const geolocationLng = pick(body, 'geolocation_lng', 'geolocationLng', geolocationClaim?.lng);

      // ── Validation ────────────────────────────────────────────────────────
      if (!evidenceId || !isValidUUID(evidenceId)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'evidenceId must be a valid UUID', null, req)
        );
      }

      if (!mediaType || !['IMAGE', 'VIDEO'].includes(mediaType.toUpperCase())) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'mediaType must be IMAGE or VIDEO', null, req)
        );
      }

      if (!sha256Original || !isValidSHA256(sha256Original)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'sha256Original must be a 64-char hex string', null, req)
        );
      }

      if (!signerDeviceKeyId || typeof signerDeviceKeyId !== 'string') {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'signerDeviceKeyId is required', null, req)
        );
      }

      if (!signature || typeof signature !== 'string') {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'signature is required', null, req)
        );
      }

      // ── Step 3.2: Idempotency logic ──────────────────────────────────────

      // Get idempotency key from header
      const idempotencyKey = getIdempotencyKey(req);

      if (!isValidIdempotencyKey(idempotencyKey)) {
        return res.status(400).json(
          buildError(
            400,
            'INVALID_IDEMPOTENCY_KEY',
            'Idempotency-Key header is required and must be a UUID.',
            null,
            req
          )
        );
      }

      const requestHash = computeRequestHash(req.body);
      const idempotencyScope = 'POST /api/v1/evidence';

      const idempotencyClaim = await claimIdempotencyKey({
        prisma,
        key: idempotencyKey,
        scope: idempotencyScope,
        requestHash,
        requestId: req.requestId,
      });

      if (idempotencyClaim.action === 'replay') {
        return res
          .status(idempotencyClaim.existing.responseCode || 200)
          .json({
            ...idempotencyClaim.existing.responseBody,
            idempotent_replay: true,
            request_id: req.requestId,
          });
      }

      if (idempotencyClaim.action === 'conflict') {
        return res.status(409).json(
          buildError(
            409,
            'IDEMPOTENCY_KEY_CONFLICT',
            'This Idempotency-Key was already used with a different request body.',
            { idempotency_key: idempotencyKey },
            req
          )
        );
      }

      if (idempotencyClaim.action === 'in_progress') {
        return res.status(409).json(
          buildError(
            409,
            'IDEMPOTENCY_REQUEST_IN_PROGRESS',
            'This Idempotency-Key is already processing. Retry the same request later.',
            { idempotency_key: idempotencyKey },
            req
          )
        );
      }

      // ── Continue: Check duplicate by device + hash ──────────────────────
      const duplicate = await prisma.evidence.findFirst({
        where: {
          signerDeviceKeyId,
          sha256Original: sha256Original.toLowerCase(),
        },
        select: { evidenceId: true },
      });

      if (duplicate) {
        return res.status(409).json(
          buildError(409, 'DUPLICATE_EVIDENCE', 'Evidence with this hash already registered from this device', {
            evidenceId: duplicate.evidenceId,
          }, req)
        );
      }

      // ── Verify device exists ──────────────────────────────────────────────
      const device = await prisma.device.findUnique({
        where: { keyId: signerDeviceKeyId },
        select: { deviceId: true, trustTier: true, revokedAt: true },
      });

      if (!device) {
        return res.status(404).json(
          buildError(404, 'DEVICE_NOT_FOUND', 'Signing device not found in registry', { signerDeviceKeyId }, req)
        );
      }

      if (device.revokedAt) {
        return res.status(403).json(
          buildError(403, 'DEVICE_REVOKED', 'Signing device has been revoked', { signerDeviceKeyId }, req)
        );
      }

      // ── Create evidence record ────────────────────────────────────────────
      const evidence = await prisma.evidence.create({
        data: {
          evidenceId,
          protocolVersion: PROTOCOL_VERSION,
          mediaType: mediaType.toUpperCase(),
          sha256Original: sha256Original.toLowerCase(),
          canonicalMediaHash: canonicalMediaHash ? canonicalMediaHash.toLowerCase() : null,
          robustFingerprintProfile: robustFingerprintProfile || {},
          fingerprintAlgorithmVersions: fingerprintAlgorithmVersions || {},
          signerDeviceKeyId,
          signerPublicKeyReference: signerPublicKeyReference || signerDeviceKeyId,
          signatureAlgorithm: signatureAlgorithm || 'SHA256withECDSA',
          deviceAttestationSummary: deviceAttestationSummary || null,
          trustedTimestampTokenReference: trustedTimestampTokenReference || null,
          captureTimeClaim: captureTimeClaim ? new Date(captureTimeClaim) : null,
          geolocationLat: geolocationLat ? parseFloat(geolocationLat) : null,
          geolocationLng: geolocationLng ? parseFloat(geolocationLng) : null,
          privacyFlags: privacyFlags || { gps: false, exif: false, device_info: false },
          clientAppVersion: clientAppVersion || 'unknown',
          verificationPolicyVersion: verificationPolicyVersion || POLICY_VERSION,
          chainParentEvidenceId: chainParentEvidenceId || null,
          auditReference: auditReference || computeSHA256Sync(`${evidenceId}-${Date.now()}`),
          signature,
          perceptualHash: perceptualHash || null,
          fingerprintPrimary: fingerprintPrimary || null,
          fingerprintSearchTokens: fingerprintSearchTokens || null,
          durationMs: durationMs ? parseInt(durationMs) : null,
          width: width ? parseInt(width) : null,
          height: height ? parseInt(height) : null,
          codec: codec || null,
          frameRate: frameRate ? parseFloat(frameRate) : null,
          transformHints: transformHints || null,
          status: 'ACTIVE',
          requestId: req.requestId,
          idempotencyKey,
        },
      });

      // ── Audit log ─────────────────────────────────────────────────────────
      await prisma.auditLog.create({
        data: {
          requestId: req.requestId,
          action: 'EVIDENCE_REGISTERED',
          entityType: 'Evidence',
          entityId: evidenceId,
          actorDeviceId: device.deviceId,
          actorKeyId: signerDeviceKeyId,
          ipAddress: req.ip,
          userAgent: req.headers['user-agent'] || null,
          details: {
            mediaType: evidence.mediaType,
            trustTier: device.trustTier,
          },
        },
      });

      console.info(`[Evidence] Registered: ${evidenceId} device=${signerDeviceKeyId}`);

      // ── Step 3.3: Build warnings and response ────────────────────────────

      const warnings = [];

      if (!captureTimeClaim) {
        warnings.push('capture_time_claim_missing');
      }

      if (!trustedTimestampTokenReference) {
        warnings.push('trusted_timestamp_token_not_available');
      }

      const responseBody = {
        ...buildEvidenceResponse(evidence, idempotencyKey, warnings),
        request_id: req.requestId,
      };

      await completeIdempotencyKey({
        prisma,
        key: idempotencyKey,
        responseCode: 201,
        responseBody,
      });

      return res.status(201).json(responseBody);
    } catch (error) {
      if (error.code === 'P2002') {
        return res.status(409).json(
          buildError(409, 'DUPLICATE_EVIDENCE', 'Evidence with this ID or audit reference already exists', null, req)
        );
      }
      next(error);
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// GET /evidence/:evidenceId
// Fetch an evidence record by ID
// ─────────────────────────────────────────────────────────────────────────────

router.get(
  '/:evidenceId',
  authenticate,
  async (req, res, next) => {
    try {
      const { evidenceId } = req.params;

      if (!isValidUUID(evidenceId)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'evidenceId must be a valid UUID', null, req)
        );
      }

      const evidence = await prisma.evidence.findUnique({
        where: { evidenceId },
      });

      if (!evidence) {
        return res.status(404).json(
          buildError(404, 'EVIDENCE_NOT_FOUND', 'Evidence record not found', { evidenceId }, req)
        );
      }

      return res.status(200).json({
        ...evidence,
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// GET /evidence/by-hash/:sha256
// Exact hash lookup
// ─────────────────────────────────────────────────────────────────────────────

router.get(
  '/by-hash/:sha256',
  authenticate,
  async (req, res, next) => {
    try {
      const { sha256 } = req.params;

      if (!isValidSHA256(sha256)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'sha256 must be a 64-character hex string', null, req)
        );
      }

      const evidence = await prisma.evidence.findFirst({
        where: {
          sha256Original: sha256.toLowerCase(),
        },
        orderBy: { createdAt: 'desc' },
      });

      if (!evidence) {
        return res.status(404).json(
          buildError(404, 'EVIDENCE_NOT_FOUND', 'No evidence found with this hash', { sha256 }, req)
        );
      }

      return res.status(200).json({
        ...evidence,
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

module.exports = router;
