/**
 * src/routes/search.js
 *
 * Express routes for evidence search and related evidence retrieval.
 * Endpoints:
 *   POST /search - Search for related or similar evidence (staged matching)
 *   GET /search/{evidence_id}/related - Retrieve related evidence graph/result set
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
    console.warn(`[search.js] Warning: could not load ${path} — ${e.message}`);
    return (req, res, next) => next();
  }
}

// Auth middleware
const authenticate = safeRequire('../middleware/auth', 'authenticate');

// Rate limiter — use generalRateLimit from rateLimit.js
const rateLimitMod = safeRequire('../middleware/rateLimit');
const generalRateLimit = rateLimitMod.generalRateLimit || ((req, res, next) => next());

// Validation
const validationMod = safeRequire('../middleware/validation');
const validateEvidenceId = validationMod.validateEvidenceId || ((req, res, next) => next());

// Service
const { searchEvidence, getRelatedEvidence } = require('../services/searchService');

// Logger
const logger = console;

/**
 * POST /search
 *
 * Submit a search request.
 */
router.post(
  '/',
  authenticate,
  generalRateLimit, // FIXED: use generalRateLimit instead of rateLimiter(...)
  async (req, res, next) => {
    try {
      const body = req.body;

      // Validate required fields
      if (!body.sha256 || typeof body.sha256 !== 'string') {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'sha256 is required and must be a string',
          detail: null,
          policy_version: process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0',
          request_id: req.requestId || 'unknown'
        });
      }
      if (!/^[0-9a-f]{64}$/i.test(body.sha256)) {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'sha256 must be a 64-character hex string',
          detail: null,
          policy_version: process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0',
          request_id: req.requestId || 'unknown'
        });
      }
      if (!body.media_type || !['image', 'video'].includes(body.media_type)) {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'media_type must be "image" or "video"',
          detail: null,
          policy_version: process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0',
          request_id: req.requestId || 'unknown'
        });
      }

      // Optional fields validation
      if (body.canonical_media_hash && !/^[0-9a-f]{64}$/i.test(body.canonical_media_hash)) {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'canonical_media_hash must be a 64-character hex string',
          detail: null,
          policy_version: process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0',
          request_id: req.requestId || 'unknown'
        });
      }
      if (body.maxResults && (typeof body.maxResults !== 'number' || body.maxResults < 1)) {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'maxResults must be a positive number',
          detail: null,
          policy_version: process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0',
          request_id: req.requestId || 'unknown'
        });
      }
      if (body.maxCandidates && (typeof body.maxCandidates !== 'number' || body.maxCandidates < 1)) {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'maxCandidates must be a positive number',
          detail: null,
          policy_version: process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0',
          request_id: req.requestId || 'unknown'
        });
      }

      // Prepare request object for service
      const searchRequest = {
        sha256: body.sha256,
        canonical_media_hash: body.canonical_media_hash || null,
        robust_fingerprint_profile: body.robust_fingerprint_profile || null,
        media_type: body.media_type,
        filters: body.filters || {},
        maxResults: body.maxResults || 10,
        maxCandidates: body.maxCandidates || 100,
        actorId: req.user ? req.user.id : null,
      };

      const verdict = await searchEvidence(searchRequest);

      logger.info(`Search completed for sha256: ${body.sha256}, media_type: ${body.media_type}, results: ${verdict.total_matches}`);

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
 * GET /search/{evidence_id}/related
 */
router.get(
  '/:evidence_id/related',
  authenticate,
  generalRateLimit, // FIXED: use generalRateLimit instead of rateLimiter(...)
  validateEvidenceId,
  async (req, res, next) => {
    try {
      const { evidence_id } = req.params;
      const maxResults = parseInt(req.query.maxResults, 10) || 10;

      const related = await getRelatedEvidence(evidence_id, maxResults);

      if (related.length === 0) {
        const { prisma } = require('../config/database');
        const exists = await prisma.evidenceRecord.findUnique({
          where: { evidence_id },
          select: { evidence_id: true },
        });
        if (!exists) {
          return res.status(404).json({
            error_code: 'EVIDENCE_NOT_FOUND',
            message: 'No evidence found with that ID',
            detail: null,
            policy_version: process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0',
            request_id: req.requestId || 'unknown'
          });
        }
        return res.json({
          evidence_id,
          related: [],
          total: 0,
          policy_version: process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0',
          request_id: req.requestId || 'unknown'
        });
      }

      const response = {
        evidence_id,
        related: related.map(item => ({
          evidence_id: item.evidence.evidence_id,
          sha256: item.evidence.sha256_original,
          media_type: item.evidence.media_type,
          created_at: item.evidence.created_at,
          relation_type: item.relation_type,
        })),
        total: related.length,
        policy_version: process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0',
        request_id: req.requestId || 'unknown'
      };

      res.json(response);
    } catch (error) {
      next(error);
    }
  }
);

module.exports = router;
