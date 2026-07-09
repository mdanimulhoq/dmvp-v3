/**
 * @file src/routes/search.js
 * @description DMVP v3.0 — Evidence Search Routes
 *
 * Endpoints:
 *   GET  /search         — Search service info
 *   POST /search         — Search for related/similar evidence
 *   GET  /search/:evidenceId/related — Get related evidence
 *
 * @module routes/search
 * @version dmvp-v3.0.0
 */

'use strict';

const express = require('express');
const router = express.Router();

const { authenticate } = require('../middleware/auth');
const { generalRateLimit } = require('../middleware/rateLimit');
const { prisma } = require('../config/database');

const POLICY_VERSION = process.env.VERIFICATION_POLICY_VERSION || 'policy-v3.0.0';

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

function isValidSHA256(str) {
  return typeof str === 'string' && /^[0-9a-f]{64}$/i.test(str);
}

// ─────────────────────────────────────────────────────────────────────────────
// GET /search
// Search service info
// ─────────────────────────────────────────────────────────────────────────────

router.get(
  '/',
  authenticate,
  async (req, res) => {
    return res.status(200).json({
      service: 'DMVP Search',
      version: '3.0.0',
      endpoints: {
        search: 'POST /',
        related: 'GET /:evidenceId/related',
      },
      policy_version: POLICY_VERSION,
      request_id: req.requestId,
    });
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// POST /search
// Search for related or similar evidence (staged matching)
// ─────────────────────────────────────────────────────────────────────────────

router.post(
  '/',
  authenticate,
  generalRateLimit,
  async (req, res, next) => {
    try {
      const {
        sha256,
        canonicalMediaHash,
        perceptualHash,
        fingerprintProfile,
        mediaType,
        maxResults,
        maxCandidates,
      } = req.body;

      // ── Validation ────────────────────────────────────────────────────────
      if (!sha256 || !isValidSHA256(sha256)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'sha256 must be a 64-character hex string', null, req)
        );
      }

      if (!mediaType || !['IMAGE', 'VIDEO'].includes(mediaType.toUpperCase())) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'mediaType must be IMAGE or VIDEO', null, req)
        );
      }

      const limit = Math.min(parseInt(maxResults) || 10, 50);
      const candidateLimit = Math.min(parseInt(maxCandidates) || 100, 500);

      // ── Stage 0: Exact lookup ─────────────────────────────────────────────
      const exactMatch = await prisma.evidence.findFirst({
        where: {
          sha256Original: sha256.toLowerCase(),
        },
        include: {
          signerDevice: {
            select: {
              deviceId: true,
              keyId: true,
              trustTier: true,
            },
          },
        },
      });

      if (exactMatch) {
        return res.status(200).json({
          stage: 'exact_match',
          total_matches: 1,
          results: [{
            evidenceId: exactMatch.evidenceId,
            sha256Original: exactMatch.sha256Original,
            mediaType: exactMatch.mediaType,
            registrationServerTime: exactMatch.registrationServerTime,
            signerDeviceKeyId: exactMatch.signerDeviceKeyId,
            trustTier: exactMatch.signerDevice?.trustTier,
            match_type: 'EXACT',
          }],
          policy_version: POLICY_VERSION,
          request_id: req.requestId,
        });
      }

      // ── Stage 1: Coarse candidate generation ────────────────────────────────
      let candidates = [];

      // Try perceptual hash prefix match
      if (perceptualHash) {
        candidates = await prisma.evidence.findMany({
          where: {
            perceptualHash: {
              startsWith: perceptualHash.substring(0, 8),
            },
            mediaType: mediaType.toUpperCase(),
          },
          take: candidateLimit,
          include: {
            signerDevice: {
              select: {
                deviceId: true,
                keyId: true,
                trustTier: true,
              },
            },
          },
        });
      }

      // Fallback: search by media type and time window
      if (candidates.length === 0) {
        candidates = await prisma.evidence.findMany({
          where: {
            mediaType: mediaType.toUpperCase(),
          },
          take: candidateLimit,
          orderBy: { createdAt: 'desc' },
          include: {
            signerDevice: {
              select: {
                deviceId: true,
                keyId: true,
                trustTier: true,
              },
            },
          },
        });
      }

      // ── Stage 2: Re-ranking (simplified) ──────────────────────────────────
      const ranked = candidates.map(c => ({
        evidenceId: c.evidenceId,
        sha256Original: c.sha256Original,
        mediaType: c.mediaType,
        registrationServerTime: c.registrationServerTime,
        signerDeviceKeyId: c.signerDeviceKeyId,
        trustTier: c.signerDevice?.trustTier,
        match_type: 'SIMILAR',
      }));

      // ── Stage 3: Verdict construction ─────────────────────────────────────
      const totalMatches = ranked.length;
      const similarityVerdict = totalMatches > 0 ? 'WEAK_SIMILARITY' : 'NO_RELIABLE_SIMILARITY';

      console.info(`[Search] ${totalMatches} candidates for ${sha256.substring(0, 16)}...`);

      return res.status(200).json({
        stage: 'similarity_search',
        total_matches: totalMatches,
        similarity_verdict: similarityVerdict,
        results: ranked.slice(0, limit),
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// GET /search/:evidenceId/related
// Get related evidence for a specific evidence record
// ─────────────────────────────────────────────────────────────────────────────

router.get(
  '/:evidenceId/related',
  authenticate,
  async (req, res, next) => {
    try {
      const { evidenceId } = req.params;
      const maxResults = Math.min(parseInt(req.query.maxResults) || 10, 50);

      // Check if evidence exists
      const evidence = await prisma.evidence.findUnique({
        where: { evidenceId },
        select: { evidenceId: true, mediaType: true, sha256Original: true },
      });

      if (!evidence) {
        return res.status(404).json(
          buildError(404, 'EVIDENCE_NOT_FOUND', 'Evidence record not found', { evidenceId }, req)
        );
      }

      // Find related: same media type, chain parent/children
      const childrenIds = await prisma.evidence.findMany({
        where: { chainParentEvidenceId: evidenceId },
        select: { evidenceId: true },
      }).then(children => children.map(c => c.evidenceId));

      const related = await prisma.evidence.findMany({
        where: {
          mediaType: evidence.mediaType,
          NOT: { evidenceId },
          OR: [
            { chainParentEvidenceId: evidenceId },
            { evidenceId: { in: childrenIds } },
          ],
        },
        take: maxResults,
        include: {
          signerDevice: {
            select: {
              keyId: true,
              trustTier: true,
            },
          },
        },
      });

      return res.status(200).json({
        evidenceId,
        total: related.length,
        related: related.map(r => ({
          evidenceId: r.evidenceId,
          sha256Original: r.sha256Original,
          mediaType: r.mediaType,
          registrationServerTime: r.registrationServerTime,
          signerDeviceKeyId: r.signerDeviceKeyId,
          trustTier: r.signerDevice?.trustTier,
        })),
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

module.exports = router;
