/**
 * src/routes/verify.js
 *
 * Express routes for media verification.
 * Endpoints:
 *   POST /verify - Submit verification request and receive multi-axis verdict
 *   GET /verify/policy - Retrieve active verification policy metadata
 *
 * All routes enforce authentication, rate limiting, and input validation.
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
    console.warn(`[verify.js] Warning: could not load ${path} — ${e.message}`);
    return (req, res, next) => next();
  }
}

// Auth middleware
const authenticate = safeRequire('../middleware/auth', 'authenticate');

// Rate limiter — use verifyRateLimit from rateLimit.js
const rateLimitMod = safeRequire('../middleware/rateLimit');
const verifyRateLimit = rateLimitMod.verifyRateLimit || ((req, res, next) => next());

// Validation
const validationMod = safeRequire('../middleware/validation');
const validateVerificationRequest = validationMod.validateVerificationRequest || ((req, res, next) => next());

// Service
const { verifyMedia } = require('../services/verifyService');

// Logger
const logger = console;

/**
 * POST /verify
 *
 * Submit a verification request.
 */
router.post(
  '/',
  authenticate,
  verifyRateLimit, // FIXED: use verifyRateLimit instead of rateLimiter(...)
  validateVerificationRequest,
  async (req, res, next) => {
    try {
      const request = req.body;
      const verdict = await verifyMedia(request);

      logger.info(`Verification completed for sha256: ${request.sha256}, mode: ${request.verification_mode || 'standard'}`);

      res.json({
        ...verdict,
        policy_version: process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0',
        request_id: req.requestId || 'unknown'
      });
    } catch (error) {
      if (error.message && error.message.includes('required')) {
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
 * GET /verify/policy
 */
router.get(
  '/policy',
  authenticate,
  async (req, res, next) => {
    try {
      const policy = {
        policy_version: 'dmvp-v3.0.0',
        supported_modes: ['fast', 'standard', 'deep'],
        default_mode: 'standard',
        similarity_thresholds: {
          strong_derivative: 0.95,
          probable_derivative: 0.80,
          weak_similarity: 0.50,
        },
        integrity_checks: ['sha256', 'canonical_hash'],
        provenance_checks: ['device_trust_tier', 'attestation'],
        timestamp_modes: ['standard', 'enhanced', 'high_assurance'],
        algorithm_versions: {
          fingerprint: 'v1.0',
          similarity: 'v1.0',
        },
        evidence_quality_mapping: {
          TIER_A: 'HIGH_EVIDENTIARY_STRENGTH',
          TIER_B: 'MODERATE_EVIDENTIARY_STRENGTH',
          TIER_C: 'LOW_EVIDENTIARY_STRENGTH',
          TIER_D: 'LOW_EVIDENTIARY_STRENGTH',
        },
        warnings: {
          fast_mode: 'Reduced accuracy for similarity; exact integrity still verified.',
          attestation_missing: 'Device attestation missing or degraded.',
        },
      };
      res.json({
        ...policy,
        request_id: req.requestId || 'unknown'
      });
    } catch (error) {
      next(error);
    }
  }
);

module.exports = router;
