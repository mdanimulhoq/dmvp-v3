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

// Middleware
const { authenticate } = require('../middleware/auth');
const { rateLimiter } = require('../middleware/rateLimit');
const { validateEvidenceId, validateSha256 } = require('../middleware/validation');

// Service
const { searchEvidence, getRelatedEvidence } = require('../services/searchService');

// Logger
const logger = console;

/**
 * POST /search
 *
 * Submit a search request.
 *
 * Request body:
 *   {
 *     sha256: string (64-char hex) - required,
 *     canonical_media_hash: string (64-char hex) - optional,
 *     robust_fingerprint_profile: object - optional (for similarity search),
 *     media_type: "image" | "video" - required,
 *     filters: { signer_device_key_id: string } - optional,
 *     maxResults: number - optional (default: 10),
 *     maxCandidates: number - optional (default: 100)
 *   }
 *
 * Response: 200 OK with search verdict (matched evidence, scores, metadata).
 *           400 Bad Request if validation fails.
 *           422 Unprocessable if missing required fields.
 */
router.post(
  '/',
  authenticate,
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 50 }), // 50 per 15 min for search
  async (req, res, next) => {
    try {
      const body = req.body;

      // Validate required fields
      if (!body.sha256 || typeof body.sha256 !== 'string') {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'sha256 is required and must be a string',
          detail: null,
        });
      }
      if (!/^[0-9a-f]{64}$/i.test(body.sha256)) {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'sha256 must be a 64-character hex string',
          detail: null,
        });
      }
      if (!body.media_type || !['image', 'video'].includes(body.media_type)) {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'media_type must be "image" or "video"',
          detail: null,
        });
      }

      // Optional fields validation
      if (body.canonical_media_hash && !/^[0-9a-f]{64}$/i.test(body.canonical_media_hash)) {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'canonical_media_hash must be a 64-character hex string',
          detail: null,
        });
      }
      if (body.maxResults && (typeof body.maxResults !== 'number' || body.maxResults < 1)) {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'maxResults must be a positive number',
          detail: null,
        });
      }
      if (body.maxCandidates && (typeof body.maxCandidates !== 'number' || body.maxCandidates < 1)) {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'maxCandidates must be a positive number',
          detail: null,
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

      // Log search event
      logger.info(`Search completed for sha256: ${body.sha256}, media_type: ${body.media_type}, results: ${verdict.total_matches}`);

      res.json(verdict);
    } catch (error) {
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
 * GET /search/{evidence_id}/related
 *
 * Retrieve related evidence graph/result set for a given evidence ID.
 * Returns records linked via lineage (parent/child) and duplicate hashes.
 *
 * Response: 200 OK with array of related evidence objects (each includes evidence and relation_type).
 *           404 Not Found if evidence_id does not exist.
 */
router.get(
  '/:evidence_id/related',
  authenticate,
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 50 }),
  validateEvidenceId, // ensures evidence_id is UUID
  async (req, res, next) => {
    try {
      const { evidence_id } = req.params;
      const maxResults = parseInt(req.query.maxResults, 10) || 10;

      // Check if the evidence exists (optional, getRelatedEvidence will return empty if not)
      const related = await getRelatedEvidence(evidence_id, maxResults);

      if (related.length === 0) {
        // Check if evidence exists
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
          });
        }
        // Evidence exists but no related records found
        return res.json({
          evidence_id,
          related: [],
          total: 0,
        });
      }

      // Format response
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
      };

      res.json(response);
    } catch (error) {
      next(error);
    }
  }
);

module.exports = router;
