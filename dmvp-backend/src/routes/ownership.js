/**
 * @file src/routes/ownership.js
 * @description DMVP v3.0 — Ownership Claim Routes
 *
 * Express routes for ownership claims and dispute management.
 *
 * Endpoints:
 *   POST   /ownership/claim                    — Submit an ownership claim
 *   GET    /ownership/:evidence_id             — List claims for evidence
 *   GET    /ownership/:evidence_id/claim/:claim_id  — Get specific claim
 *   PUT    /ownership/:evidence_id/claim/:claim_id/review  — Admin review claim
 *   DELETE /ownership/:evidence_id/claim/:claim_id  — Withdraw claim (owner only)
 *
 * All routes enforce authentication, rate limiting, and structured error responses.
 *
 * @module routes/ownership
 * @version dmvp-v3.0.0
 */

'use strict';

const express = require('express');
const router = express.Router();

const { authenticate } = require('../middleware/auth');
const { rateLimiter } = require('../middleware/rateLimit');
const { prisma } = require('../config/database');

const POLICY_VERSION = process.env.VERIFICATION_POLICY_VERSION || 'dmvp-v3.0.0';

// ─────────────────────────────────────────────────────────────────────────────
// Helper: Build structured error response
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

// ─────────────────────────────────────────────────────────────────────────────
// Helper: Validate UUID format
// ─────────────────────────────────────────────────────────────────────────────

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function isValidUUID(str) {
  return typeof str === 'string' && UUID_REGEX.test(str);
}

// ─────────────────────────────────────────────────────────────────────────────
// POST /ownership/claim
// Submit an ownership claim for an evidence record.
// ─────────────────────────────────────────────────────────────────────────────

router.post(
  '/claim',
  authenticate,
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 30 }),
  async (req, res, next) => {
    try {
      const { evidence_id, claimant_identity, claim_type, supporting_data } = req.body;

      // ── Input validation ─────────────────────────────────────────────────
      if (!evidence_id || typeof evidence_id !== 'string') {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'evidence_id is required and must be a string', null, req)
        );
      }

      if (!claimant_identity || typeof claimant_identity !== 'string' || claimant_identity.trim().length === 0) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'claimant_identity is required and must be a non-empty string', null, req)
        );
      }

      const validClaimTypes = ['original_author', 'license_holder', 'custodian', 'other'];
      if (!claim_type || !validClaimTypes.includes(claim_type)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', `claim_type must be one of: ${validClaimTypes.join(', ')}`, null, req)
        );
      }

      // ── Verify evidence exists ────────────────────────────────────────────
      const evidence = await prisma.evidenceRecord.findUnique({
        where: { evidence_id },
        select: { evidence_id: true, media_type: true },
      });

      if (!evidence) {
        return res.status(404).json(
          buildError(404, 'EVIDENCE_NOT_FOUND', 'Evidence record not found', { evidence_id }, req)
        );
      }

      // ── Check for duplicate claim ─────────────────────────────────────────
      const existing = await prisma.ownershipClaim.findFirst({
        where: {
          evidence_id,
          claimant_identity: claimant_identity.trim(),
        },
        select: { claim_id: true, review_status: true },
      });

      if (existing) {
        return res.status(409).json(
          buildError(409, 'DUPLICATE_CLAIM', 'A claim already exists for this evidence and claimant', {
            claim_id: existing.claim_id,
            status: existing.review_status,
          }, req)
        );
      }

      // ── Create claim ──────────────────────────────────────────────────────
      const claim = await prisma.ownershipClaim.create({
        data: {
          evidence_id,
          claimant_identity: claimant_identity.trim(),
          claim_type,
          claim_timestamp: new Date(),
          review_status: 'PENDING',
          supporting_data: supporting_data && typeof supporting_data === 'object'
            ? JSON.stringify(supporting_data)
            : null,
        },
      });

      // ── Audit log ─────────────────────────────────────────────────────────
      await prisma.auditLog.create({
        data: {
          event_type: 'OWNERSHIP_CLAIM_SUBMITTED',
          actor: req.user?.id || claimant_identity.trim(),
          target: evidence_id,
          timestamp: new Date(),
          policy_version: POLICY_VERSION,
          metadata: {
            claim_id: claim.claim_id,
            claim_type,
            evidence_media_type: evidence.media_type,
          },
        },
      });

      console.info(`[Ownership] Claim submitted: evidence=${evidence_id} claimant=${claimant_identity.trim()}`);

      return res.status(201).json({
        success: true,
        claim_id: claim.claim_id,
        evidence_id: claim.evidence_id,
        claimant_identity: claim.claimant_identity,
        claim_type: claim.claim_type,
        review_status: claim.review_status,
        claim_timestamp: claim.claim_timestamp,
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      if (error.code === 'P2003') {
        return res.status(404).json(
          buildError(404, 'EVIDENCE_NOT_FOUND', 'Evidence record not found (foreign key constraint)', null, req)
        );
      }
      next(error);
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// GET /ownership/:evidence_id
// Retrieve all claims for a given evidence record.
// ─────────────────────────────────────────────────────────────────────────────

router.get(
  '/:evidence_id',
  authenticate,
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 100 }),
  async (req, res, next) => {
    try {
      const { evidence_id } = req.params;

      if (!isValidUUID(evidence_id)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'evidence_id must be a valid UUID', null, req)
        );
      }

      // Verify evidence exists
      const evidence = await prisma.evidenceRecord.findUnique({
        where: { evidence_id },
        select: { evidence_id: true },
      });

      if (!evidence) {
        return res.status(404).json(
          buildError(404, 'EVIDENCE_NOT_FOUND', 'Evidence record not found', { evidence_id }, req)
        );
      }

      const claims = await prisma.ownershipClaim.findMany({
        where: { evidence_id },
        orderBy: { created_at: 'desc' },
        select: {
          claim_id: true,
          evidence_id: true,
          claimant_identity: true,
          claim_type: true,
          claim_timestamp: true,
          review_status: true,
          review_notes: true,
          reviewed_at: true,
          reviewed_by: true,
          created_at: true,
          updated_at: true,
        },
      });

      return res.status(200).json({
        evidence_id,
        claim_count: claims.length,
        claims,
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// GET /ownership/:evidence_id/claim/:claim_id
// Get a specific claim by ID.
// ─────────────────────────────────────────────────────────────────────────────

router.get(
  '/:evidence_id/claim/:claim_id',
  authenticate,
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 100 }),
  async (req, res, next) => {
    try {
      const { evidence_id, claim_id } = req.params;

      if (!isValidUUID(evidence_id)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'evidence_id must be a valid UUID', null, req)
        );
      }

      if (!claim_id || typeof claim_id !== 'string') {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'claim_id is required', null, req)
        );
      }

      const claim = await prisma.ownershipClaim.findFirst({
        where: {
          claim_id,
          evidence_id,
        },
        select: {
          claim_id: true,
          evidence_id: true,
          claimant_identity: true,
          claim_type: true,
          claim_timestamp: true,
          review_status: true,
          review_notes: true,
          reviewed_at: true,
          reviewed_by: true,
          supporting_data: true,
          created_at: true,
          updated_at: true,
        },
      });

      if (!claim) {
        return res.status(404).json(
          buildError(404, 'CLAIM_NOT_FOUND', 'Claim not found for this evidence', { evidence_id, claim_id }, req)
        );
      }

      // Parse supporting_data if stored as JSON string
      let parsedSupportingData = null;
      if (claim.supporting_data) {
        try {
          parsedSupportingData = JSON.parse(claim.supporting_data);
        } catch {
          parsedSupportingData = claim.supporting_data;
        }
      }

      return res.status(200).json({
        ...claim,
        supporting_data: parsedSupportingData,
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// PUT /ownership/:evidence_id/claim/:claim_id/review
// Admin endpoint to review and update claim status.
// ─────────────────────────────────────────────────────────────────────────────

router.put(
  '/:evidence_id/claim/:claim_id/review',
  authenticate,
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 50 }),
  async (req, res, next) => {
    try {
      const { evidence_id, claim_id } = req.params;
      const { review_status, review_notes } = req.body;

      if (!isValidUUID(evidence_id)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'evidence_id must be a valid UUID', null, req)
        );
      }

      if (!claim_id || typeof claim_id !== 'string') {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'claim_id is required', null, req)
        );
      }

      const validStatuses = ['APPROVED', 'REJECTED', 'PENDING'];
      if (!review_status || !validStatuses.includes(review_status)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', `review_status must be one of: ${validStatuses.join(', ')}`, null, req)
        );
      }

      // Check claim exists
      const existing = await prisma.ownershipClaim.findFirst({
        where: {
          claim_id,
          evidence_id,
        },
      });

      if (!existing) {
        return res.status(404).json(
          buildError(404, 'CLAIM_NOT_FOUND', 'Claim not found for this evidence', { evidence_id, claim_id }, req)
        );
      }

      // Update claim
      const updated = await prisma.ownershipClaim.update({
        where: { claim_id },
        data: {
          review_status,
          review_notes: review_notes || null,
          reviewed_at: new Date(),
          reviewed_by: req.user?.id || 'system',
        },
      });

      // Audit log
      await prisma.auditLog.create({
        data: {
          event_type: 'OWNERSHIP_CLAIM_REVIEWED',
          actor: req.user?.id || 'system',
          target: evidence_id,
          timestamp: new Date(),
          policy_version: POLICY_VERSION,
          metadata: {
            claim_id,
            previous_status: existing.review_status,
            new_status: review_status,
            review_notes: review_notes || null,
          },
        },
      });

      console.info(`[Ownership] Claim reviewed: claim=${claim_id} status=${review_status} reviewer=${req.user?.id || 'system'}`);

      return res.status(200).json({
        success: true,
        claim_id: updated.claim_id,
        evidence_id: updated.evidence_id,
        review_status: updated.review_status,
        review_notes: updated.review_notes,
        reviewed_at: updated.reviewed_at,
        reviewed_by: updated.reviewed_by,
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// DELETE /ownership/:evidence_id/claim/:claim_id
// Withdraw a claim (only by the claimant or admin).
// ─────────────────────────────────────────────────────────────────────────────

router.delete(
  '/:evidence_id/claim/:claim_id',
  authenticate,
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 20 }),
  async (req, res, next) => {
    try {
      const { evidence_id, claim_id } = req.params;

      if (!isValidUUID(evidence_id)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'evidence_id must be a valid UUID', null, req)
        );
      }

      if (!claim_id || typeof claim_id !== 'string') {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'claim_id is required', null, req)
        );
      }

      const claim = await prisma.ownershipClaim.findFirst({
        where: {
          claim_id,
          evidence_id,
        },
      });

      if (!claim) {
        return res.status(404).json(
          buildError(404, 'CLAIM_NOT_FOUND', 'Claim not found for this evidence', { evidence_id, claim_id }, req)
        );
      }

      // Only claimant or admin can withdraw
      const currentUser = req.user?.id || 'anonymous';
      const isClaimant = claim.claimant_identity === currentUser;
      const isAdmin = req.user?.role === 'admin';

      if (!isClaimant && !isAdmin) {
        return res.status(403).json(
          buildError(403, 'FORBIDDEN', 'Only the claimant or an admin can withdraw this claim', null, req)
        );
      }

      await prisma.ownershipClaim.delete({
        where: { claim_id },
      });

      // Audit log
      await prisma.auditLog.create({
        data: {
          event_type: 'OWNERSHIP_CLAIM_WITHDRAWN',
          actor: currentUser,
          target: evidence_id,
          timestamp: new Date(),
          policy_version: POLICY_VERSION,
          metadata: {
            claim_id,
            claimant_identity: claim.claimant_identity,
            withdrawn_by: currentUser,
          },
        },
      });

      console.info(`[Ownership] Claim withdrawn: claim=${claim_id} by=${currentUser}`);

      return res.status(200).json({
        success: true,
        message: 'Claim withdrawn successfully',
        claim_id,
        evidence_id,
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

module.exports = router;
