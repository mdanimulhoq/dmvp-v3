/**
 * @file src/middleware/validation.js
 * @description Request validation middleware for the DMVP v3.0 backend API.
 *
 * Defines Joi schemas for every write-bearing and query-bearing endpoint
 * group described in the DMVP v3.0 TDD / SRS / API Specification, and
 * exposes a small `validate(schemaName, source)` middleware factory that
 * validates the relevant part of the request (`body`, `query`, or `params`)
 * against the matching schema.
 *
 * On validation failure, the middleware short-circuits the request with a
 * structured error envelope consistent with the rest of the DMVP API:
 *
 *   {
 *     error_code: "VALIDATION_ERROR",
 *     message: string,
 *     detail: { fields: Array<{ field: string, issue: string }> },
 *     policy_version: string,
 *     request_id: string
 *   }
 *
 * On success, `req[source]` is replaced with the validated (and
 * type-coerced / defaulted) value so downstream handlers can trust its
 * shape without re-checking types.
 */

'use strict';

const Joi = require('joi');
const crypto = require('crypto');

/* ------------------------------------------------------------------ *
 * Shared primitive schemas
 * ------------------------------------------------------------------ */

/** 64-character lowercase or uppercase hex string (SHA-256 digest). */
const sha256Hex = Joi.string()
  .pattern(/^[a-fA-F0-9]{64}$/)
  .message('must be a 64-character hexadecimal SHA-256 digest');

/** UUID (v4) or ULID (26-char Crockford base32) evidence/record identifier. */
const idString = Joi.string()
  .pattern(/^[0-9a-fA-F-]{36}$|^[0-9A-HJKMNP-TV-Za-hjkmnp-tv-z]{26}$/)
  .message('must be a valid UUID or ULID');

const mediaType = Joi.string().valid('image', 'video');

const protocolVersion = Joi.string()
  .pattern(/^dmvp-v\d+\.\d+\.\d+$/)
  .message('must match the pattern dmvp-vX.Y.Z');

const isoTimestamp = Joi.string().isoDate();

const base64String = Joi.string().pattern(/^[A-Za-z0-9+/]+={0,2}$/).message(
  'must be a valid Base64-encoded string'
);

const geolocationClaim = Joi.object({
  lat: Joi.number().min(-90).max(90).required(),
  lng: Joi.number().min(-180).max(180).required(),
});

const privacyFlags = Joi.object({
  gps: Joi.boolean().required(),
  exif: Joi.boolean().required(),
  device_info: Joi.boolean().required(),
});

/* ------------------------------------------------------------------ *
 * Evidence registration: POST /evidence
 * ------------------------------------------------------------------ */

const evidenceRegisterSchema = Joi.object({
  protocol_version: protocolVersion.required(),
  evidence_id: idString.required(),
  media_type: mediaType.required(),
  sha256_original: sha256Hex.required(),
  canonical_media_hash: sha256Hex.optional(),
  robust_fingerprint_profile: Joi.object().unknown(true).required(),
  fingerprint_algorithm_versions: Joi.object().unknown(true).required(),
  signer_device_key_id: Joi.string().min(1).max(256).required(),
  signer_public_key_reference: Joi.string().min(1).max(2048).required(),
  signature_algorithm: Joi.string().valid('SHA256withECDSA').required(),
  device_attestation_summary: Joi.object().unknown(true).optional(),
  registration_server_time: isoTimestamp.optional(),
  trusted_timestamp_token_reference: Joi.string().optional(),
  capture_time_claim: isoTimestamp.optional(),
  geolocation_claim: geolocationClaim.optional(),
  privacy_flags: privacyFlags.required(),
  client_app_version: Joi.string().min(1).max(64).required(),
  verification_policy_version: Joi.string().min(1).max(64).optional(),
  chain_parent_evidence_id: idString.optional(),
  audit_reference: Joi.string().optional(),
  signature: base64String.required(),
}).required();

/* ------------------------------------------------------------------ *
 * Evidence lookup: GET /evidence/:id, GET /evidence/by-hash/:sha256
 * ------------------------------------------------------------------ */

const evidenceIdParamSchema = Joi.object({
  id: idString.required(),
}).required();

const evidenceHashParamSchema = Joi.object({
  sha256: sha256Hex.required(),
}).required();

/* ------------------------------------------------------------------ *
 * Verification: POST /verify
 * ------------------------------------------------------------------ */

const verifyRequestSchema = Joi.object({
  media_type: mediaType.required(),
  verification_mode: Joi.string()
    .valid('FAST', 'STANDARD', 'DEEP')
    .default('STANDARD'),
  sha256_original: sha256Hex.optional(),
  canonical_media_hash: sha256Hex.optional(),
  robust_fingerprint_profile: Joi.object().unknown(true).optional(),
  fingerprint_algorithm_versions: Joi.object().unknown(true).optional(),
  claimed_evidence_id: idString.optional(),
  client_app_version: Joi.string().min(1).max(64).required(),
})
  .or('sha256_original', 'robust_fingerprint_profile', 'claimed_evidence_id')
  .required();

/* ------------------------------------------------------------------ *
 * Search: POST /search, GET /search/:evidence_id/related
 * ------------------------------------------------------------------ */

const searchRequestSchema = Joi.object({
  media_type: mediaType.required(),
  sha256_original: sha256Hex.optional(),
  robust_fingerprint_profile: Joi.object().unknown(true).optional(),
  search_mode: Joi.string().valid('EXACT', 'APPROXIMATE', 'BOTH').default('BOTH'),
  limit: Joi.number().integer().min(1).max(100).default(20),
  cursor: Joi.string().optional(),
})
  .or('sha256_original', 'robust_fingerprint_profile')
  .required();

const searchRelatedParamSchema = Joi.object({
  evidence_id: idString.required(),
}).required();

/* ------------------------------------------------------------------ *
 * Device lifecycle: POST /devices/register|:id/rotate|:id/revoke|recover
 * ------------------------------------------------------------------ */

const deviceRegisterSchema = Joi.object({
  device_public_key: Joi.string().min(1).max(2048).required(),
  key_algorithm: Joi.string().valid('EC_P256').default('EC_P256'),
  hardware_backed: Joi.boolean().required(),
  attestation_token: Joi.string().optional(),
  platform: Joi.string().valid('android', 'ios', 'desktop').required(),
  client_app_version: Joi.string().min(1).max(64).required(),
}).required();

const deviceKeyIdParamSchema = Joi.object({
  device_key_id: Joi.string().min(1).max(256).required(),
}).required();

const deviceRotateSchema = Joi.object({
  new_device_public_key: Joi.string().min(1).max(2048).required(),
  key_algorithm: Joi.string().valid('EC_P256').default('EC_P256'),
  hardware_backed: Joi.boolean().required(),
  attestation_token: Joi.string().optional(),
  rotation_signature: base64String.required(),
}).required();

const deviceRevokeSchema = Joi.object({
  reason: Joi.string()
    .valid('LOST', 'STOLEN', 'COMPROMISED', 'REPLACED', 'OTHER')
    .required(),
  detail: Joi.string().max(1024).optional(),
}).required();

const deviceRecoverSchema = Joi.object({
  account_id: Joi.string().min(1).max(256).required(),
  recovery_method: Joi.string()
    .valid('RECOVERY_CODE', 'QUORUM_APPROVAL', 'ACCOUNT_VERIFICATION')
    .required(),
  recovery_proof: Joi.string().min(1).required(),
  new_device_public_key: Joi.string().min(1).max(2048).required(),
  hardware_backed: Joi.boolean().required(),
  platform: Joi.string().valid('android', 'ios', 'desktop').required(),
}).required();

/* ------------------------------------------------------------------ *
 * Ownership: POST /ownership/claim, GET /ownership/:evidence_id
 * ------------------------------------------------------------------ */

const ownershipClaimSchema = Joi.object({
  evidence_id: idString.required(),
  claimant_reference: Joi.string().min(1).max(256).required(),
  claim_type: Joi.string()
    .valid('ORIGINAL_CREATOR', 'LICENSEE', 'ASSIGNEE', 'DISPUTE')
    .required(),
  supporting_note: Joi.string().max(2048).optional(),
}).required();

const ownershipEvidenceIdParamSchema = Joi.object({
  evidence_id: idString.required(),
}).required();

/* ------------------------------------------------------------------ *
 * Schema registry
 * ------------------------------------------------------------------ */

const SCHEMAS = {
  evidenceRegister: evidenceRegisterSchema,
  evidenceIdParam: evidenceIdParamSchema,
  evidenceHashParam: evidenceHashParamSchema,
  verifyRequest: verifyRequestSchema,
  searchRequest: searchRequestSchema,
  searchRelatedParam: searchRelatedParamSchema,
  deviceRegister: deviceRegisterSchema,
  deviceKeyIdParam: deviceKeyIdParamSchema,
  deviceRotate: deviceRotateSchema,
  deviceRevoke: deviceRevokeSchema,
  deviceRecover: deviceRecoverSchema,
  ownershipClaim: ownershipClaimSchema,
  ownershipEvidenceIdParam: ownershipEvidenceIdParamSchema,
};

/* ------------------------------------------------------------------ *
 * Shared helpers (mirrors src/middleware/rateLimit.js conventions)
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
 * Flatten a Joi validation error into a compact, machine-readable list
 * of field-level issues suitable for the DMVP structured error envelope.
 *
 * @param {import('joi').ValidationError} joiError
 * @returns {Array<{ field: string, issue: string }>}
 */
function formatJoiError(joiError) {
  return joiError.details.map((detail) => ({
    field: detail.path.join('.') || '(root)',
    issue: detail.message.replace(/"/g, ''),
  }));
}

/**
 * Build an Express middleware that validates `req[source]` against the
 * named schema in the schema registry.
 *
 * On success, `req[source]` is reassigned to the validated/coerced value
 * (defaults applied, unknown keys stripped for body payloads) and control
 * passes to `next()`.
 *
 * On failure, responds with HTTP 400 and the DMVP structured error
 * envelope; downstream handlers are never invoked.
 *
 * @param {keyof typeof SCHEMAS} schemaName - Key into the schema registry.
 * @param {'body' | 'query' | 'params'} [source='body'] - Which part of the
 *   request to validate.
 * @returns {(req: import('express').Request, res: import('express').Response, next: import('express').NextFunction) => void}
 */
function validate(schemaName, source = 'body') {
  const schema = SCHEMAS[schemaName];

  if (!schema) {
    throw new Error(
      `validation middleware misconfigured: unknown schema "${schemaName}"`
    );
  }

  return function validationMiddleware(req, res, next) {
    const { error, value } = schema.validate(req[source], {
      abortEarly: false,
      stripUnknown: source === 'body',
      convert: true,
    });

    if (error) {
      const requestId = resolveRequestId(req);

      res.status(400).json({
        error_code: 'VALIDATION_ERROR',
        message: 'The request failed schema validation.',
        detail: {
          fields: formatJoiError(error),
        },
        policy_version: getPolicyVersion(),
        request_id: requestId,
      });
      return;
    }

    req[source] = value;
    next();
  };
}

module.exports = {
  validate,
  schemas: SCHEMAS,
};
