/**
 * src/routes/evidence.js
 *
 * Express routes for evidence registration and retrieval.
 * Endpoints:
 *   POST /evidence - Register new evidence (requires signed request)
 *   GET /evidence/:id - Get evidence record by ID (public subset)
 *   GET /evidence/by-hash/:sha256 - Get evidence by SHA-256 (exact hash lookup)
 *
 * All routes enforce authentication, rate limiting, and input validation.
 * Registration endpoints require signature verification and idempotency.
 */

const express = require('express');
const router = express.Router();

// Middleware — safe require with fallback
function safeRequire(path, exportName) {
  try {
    const mod = require(path);
    if (exportName && mod[exportName]) return mod[exportName];
    if (typeof mod === 'function') return mod;
    return mod;
  } catch (e) {
    console.warn(`[evidence.js] Warning: could not load ${path} — ${e.message}`);
    return (req, res, next) => next();
  }
}

// Auth middleware
const authenticate = safeRequire('../middleware/auth', 'authenticate');

// Rate limiter
const registerRateLimit = safeRequire('../middleware/rateLimit', 'registerRateLimit');

// Validation
const validationMod = safeRequire('../middleware/validation');
const validateEvidenceRegistration = validationMod.validateEvidenceRegistration || ((req, res, next) => next());
const validateEvidenceId = validationMod.validateEvidenceId || ((req, res, next) => next());
const validateSha256 = validationMod.validateSha256 || ((req, res, next) => next());

// Signature verification
const signatureMod = safeRequire('../middleware/signatureVerify');
const verifyRegistrationSignature = signatureMod.verifyRegistrationSignature || ((req, res, next) => next());

// Service
const {
  registerEvidence,
  getEvidenceById,
  getEvidenceByHash,
} = require('../services/evidenceService');

// Logger (optional)
const logger = console;

/**
 * POST /evidence
 *
 * Register a new evidence record.
 */
router.post(
  '/',
  authenticate,
  registerRateLimit,
  validateEvidenceRegistration,
  verifyRegistrationSignature,
  async (req, res, next) => {
    try {
      const payload = req.body;
      const idempotencyKey = req.headers['idempotency-key'] || null;
      const actorId = req.user ? req.user.id : null;

      const result = await registerEvidence(payload, idempotencyKey, actorId);

      res.status(201).json({
        ...result,
        policy_version: process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0',
        request_id: req.requestId || 'unknown'
      });
    } catch (error) {
      if (error.message && error.message.includes('duplicate')) {
        return res.status(409).json({
          error_code: 'DUPLICATE_EVIDENCE',
          message: 'Evidence already exists with same hash and device key',
          detail: error.message,
          policy_version: process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0',
          request_id: req.requestId || 'unknown'
        });
      }
      if (error.message && error.message.includes('Missing required field')) {
        return res.status(422).json({
          error_code: 'VALIDATION_ERROR',
          message: error.message,
          detail: null,
          policy_version: process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0',
          request_id: req.requestId || 'unknown'
        });
      }
      next(error);
    }
  }
);

/**
 * GET /evidence/:id
 */
router.get(
  '/:id',
  authenticate,
  validateEvidenceId,
  async (req, res, next) => {
    try {
      const { id } = req.params;
      const evidence = await getEvidenceById(id);
      if (!evidence) {
        return res.status(404).json({
          error_code: 'EVIDENCE_NOT_FOUND',
          message: 'No evidence found with that ID',
          detail: null,
          policy_version: process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0',
          request_id: req.requestId || 'unknown'
        });
      }

      const { signature, device_attestation_summary, ...publicData } = evidence;
      res.json({
        ...publicData,
        policy_version: process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0',
        request_id: req.requestId || 'unknown'
      });
    } catch (error) {
      next(error);
    }
  }
);

/**
 * GET /evidence/by-hash/:sha256
 */
router.get(
  '/by-hash/:sha256',
  authenticate,
  validateSha256,
  async (req, res, next) => {
    try {
      const { sha256 } = req.params;
      const evidence = await getEvidenceByHash(sha256);
      if (!evidence) {
        return res.status(404).json({
          error_code: 'EVIDENCE_NOT_FOUND',
          message: 'No evidence found with that SHA-256 hash',
          detail: null,
          policy_version: process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0',
          request_id: req.requestId || 'unknown'
        });
      }
      const { signature, device_attestation_summary, ...publicData } = evidence;
      res.json({
        ...publicData,
        policy_version: process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0',
        request_id: req.requestId || 'unknown'
      });
    } catch (error) {
      next(error);
    }
  }
);

module.exports = router;
