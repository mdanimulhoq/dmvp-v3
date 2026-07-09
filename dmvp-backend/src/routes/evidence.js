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

// Middleware
const { authenticate } = require('../middleware/auth');
const { registerRateLimit } = require('../middleware/rateLimit'); // FIXED: use registerRateLimit instead of rateLimiter
const { validateEvidenceRegistration, validateEvidenceId, validateSha256 } = require('../middleware/validation');
const { verifyRegistrationSignature } = require('../middleware/signatureVerify');

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
 *
 * Request body: Canonical Evidence Envelope (CEE) as JSON.
 * Headers:
 *   - Authorization: Bearer <token> (optional, but recommended)
 *   - Idempotency-Key: <string> (optional, for idempotency)
 *   - X-Request-Signature: <base64 signature> (required)
 *   - X-Nonce: <string> (required for replay protection)
 *   - X-Timestamp: <ISO timestamp> (required for replay protection)
 *
 * Response: 201 Created with the evidence record.
 *           409 Conflict if duplicate detected.
 *           422 Validation error.
 *           400 Bad request.
 */
router.post(
  '/',
  authenticate, // Optional: if we have user auth, but registration may be anonymous with device key
  registerRateLimit, // FIXED: use registerRateLimit middleware directly
  validateEvidenceRegistration, // Validates the request body structure
  verifyRegistrationSignature, // Verifies the signature and nonce/timestamp
  async (req, res, next) => {
    try {
      const payload = req.body;
      const idempotencyKey = req.headers['idempotency-key'] || null;
      const actorId = req.user ? req.user.id : null; // If authenticated

      const result = await registerEvidence(payload, idempotencyKey, actorId);

      // Check if it's a duplicate (existing record returned)
      const status = result.created_at === result.updated_at ? 201 : 200;
      res.status(status).json(result);
    } catch (error) {
      // Handle specific errors
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
      // Pass other errors to global error handler
      next(error);
    }
  }
);

/**
 * GET /evidence/:id
 *
 * Retrieve evidence record by its evidence_id.
 *
 * Returns public subset of the evidence (excluding sensitive fields like signature,
 * device attestation summary, and possibly owner info depending on permissions).
 *
 * Response: 200 OK with evidence record (filtered).
 *           404 Not Found if not exists.
 */
router.get(
  '/:id',
  authenticate, // Require authentication to view evidence
  validateEvidenceId, // validates that id is a UUID
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

      // Filter sensitive fields for public view
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
 *
 * Exact hash lookup: retrieve evidence by its SHA-256 original hash.
 *
 * Returns the evidence record(s) matching the hash (there should be one or more).
 * Usually there is exactly one, but could be multiple if same file registered by different devices.
 *
 * Response: 200 OK with array of evidence records (filtered).
 *           404 Not Found if none.
 */
router.get(
  '/by-hash/:sha256',
  authenticate,
  validateSha256, // ensures 64-char hex
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
      // Filter sensitive fields as above
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
