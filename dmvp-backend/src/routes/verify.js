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

// Middleware
const { authenticate } = require('../middleware/auth');
const { rateLimiter } = require('../middleware/rateLimit');
const { validateVerificationRequest } = require('../middleware/validation');

// Service
const { verifyMedia } = require('../services/verifyService');

// Logger
const logger = console;

/**
 * POST /verify
 *
 * Submit a verification request.
 *
 * Request body:
 *   {
 *     sha256: string (64-char hex) - required,
 *     canonical_media_hash: string (64-char hex) - optional,
 *     media_type: "image" | "video" - required,
 *     robust_fingerprint_profile: object - optional (required for similarity),
 *     verification_mode: "fast" | "standard" | "deep" - optional (default: "standard"),
 *     signer_device_key_id: string - optional (for provenance context),
 *     timestamp_info: object - optional (for time context)
 *   }
 *
 * Response: 200 OK with multi-axis verdict.
 *           400 Bad Request if validation fails.
 *           422 Unprocessable if missing required fields.
 */
router.post(
  '/',
  authenticate, // Optional: require authentication for verification
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 200 }), // 200 per 15 min
  validateVerificationRequest, // Validates required fields and formats
  async (req, res, next) => {
    try {
      const request = req.body;

      // Call verification service
      const verdict = await verifyMedia(request);

      // Log verification event (async, don't wait)
      // In production, you'd store verification events in DB.
      logger.info(`Verification completed for sha256: ${request.sha256}, mode: ${request.verification_mode || 'standard'}`);

      // Return verdict
      res.json(verdict);
    } catch (error) {
      // Handle known errors
      if (error.message && error.message.includes('required')) {
        return res.status(422).json({
          error_code: 'VALIDATION_ERROR',
          message: error.message,
          detail: null,
        });
      }
      next(error);
    }
  }
);

/**
 * GET /verify/policy
 *
 * Retrieve active verification policy metadata.
 *
 * Response: 200 OK with policy version, supported modes, thresholds, etc.
 */
router.get(
  '/policy',
  authenticate, // Optional: could be public, but we'll require auth for consistency
  async (req, res, next) => {
    try {
      // In production, this would be loaded from a policy table or config.
      // For now, return static policy information.
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
      res.json(policy);
    } catch (error) {
      next(error);
    }
  }
);

module.exports = router;
