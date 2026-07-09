/**
 * @file src/routes/ownership.js
 * @description DMVP v3.0 — Ownership Claim Routes
 *
 * Express routes for ownership claims and dispute management.
 * Aligned with prisma/schema.prisma OwnershipClaim model.
 *
 * Endpoints:
 *   POST   /ownership/claim                    — Submit an ownership claim
 *   GET    /ownership/:evidenceId              — List claims for evidence
 *   GET    /ownership/:evidenceId/claim/:claimId    — Get specific claim
 *   PUT    /ownership/:evidenceId/claim/:claimId    — Update claim status (admin)
 *   DELETE /ownership/:evidenceId/claim/:claimId    — Revoke claim
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

// ─────────────────────────────────────────────────────────────────────────────
// POST /ownership/claim
// Submit an ownership claim for an evidence record.
// Body: { evidenceId, claimantPublicKeyReference, claimType, claimStatement?, signature }
// ─────────────────────────────────────────────────────────────────────────────

router.post(
  '/claim',
  authenticate,
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 30 }),
  async (req, res, next) => {
    try {
      const {
        evidenceId,
        claimantPublicKeyReference,
        claimType,
        claimStatement,
        signature,
        signatureAlgorithm,
      } = req.body;

      // ── Validation ────────────────────────────────────────────────────────
      if (!evidenceId || typeof evidenceId !== 'string') {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'evidenceId is required and must be a string', null, req)
        );
      }

      if (!claimantPublicKeyReference || typeof claimantPublicKeyReference !== 'string' || claimantPublicKeyReference.trim().length === 0) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'claimantPublicKeyReference is required and must be a non-empty string', null, req)
        );
      }

      const validClaimTypes = ['ORIGINAL_CREATOR', 'RIGHTFUL_OWNER', 'CUSTODIAN'];
      if (!claimType || !validClaimTypes.includes(claimType)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', `claimType must be one of: ${validClaimTypes.join(', ')}`, null, req)
        );
      }

      if (!signature || typeof signature !== 'string') {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'signature is required', null, req)
        );
      }

      // ── Verify evidence exists ────────────────────────────────────────────
      const evidence = await prisma.evidence.findUnique({
        where: { evidenceId },
        select: { evidenceId: true, mediaType: true },
      });

      if (!evidence) {
        return res.status(404).json(
          buildError(404, 'EVIDENCE_NOT_FOUND', 'Evidence record not found', { evidenceId }, req)
        );
      }

      // ── Check for duplicate claim ─────────────────────────────────────────
      const existing = await prisma.ownershipClaim.findFirst({
        where: {
          evidenceId,
          claimantPublicKeyReference: claimantPublicKeyReference.trim(),
        },
        select: { ownershipClaimId: true, status: true },
      });

      if (existing) {
        return res.status(409).json(
          buildError(409, 'DUPLICATE_CLAIM', 'A claim already exists for this evidence and claimant', {
            claimId: existing.ownershipClaimId,
            status: existing.status,
          }, req)
        );
      }

      // ── Create claim ──────────────────────────────────────────────────────
      const claim = await prisma.ownershipClaim.create({
        data: {
          evidenceId,
          claimantDeviceId: req.user?.deviceId || null,
          claimantPublicKeyReference: claimantPublicKeyReference.trim(),
          claimType,
          claimStatement: claimStatement || null,
          signature,
          signatureAlgorithm: signatureAlgorithm || 'SHA256withECDSA',
          status: 'PENDING',
          requestId: req.requestId,
          metadata: req.body.metadata || null,
        },
      });

      // ── Audit log ─────────────────────────────────────────────────────────
      await prisma.auditLog.create({
        data: {
          requestId: req.requestId,
          action: 'OWNERSHIP_CLAIM_SUBMITTED',
          entityType: 'OwnershipClaim',
          entityId: claim.ownershipClaimId,
          actorDeviceId: req.user?.deviceId || null,
          actorKeyId: claimantPublicKeyReference.trim(),
          ipAddress: req.ip,
          userAgent: req.headers['user-agent'] || null,
          details: {
            evidenceId,
            claimType,
            mediaType: evidence.mediaType,
          },
        },
      });

      console.info(`[Ownership] Claim submitted: evidence=${evidenceId} claimant=${claimantPublicKeyReference.trim()}`);

      return res.status(201).json({
        success: true,
        claimId: claim.ownershipClaimId,
        evidenceId: claim.evidenceId,
        claimantPublicKeyReference: claim.claimantPublicKeyReference,
        claimType: claim.claimType,
        status: claim.status,
        createdAt: claim.createdAt,
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
// GET /ownership/:evidenceId
// Retrieve all claims for a given evidence record.
// ─────────────────────────────────────────────────────────────────────────────

router.get(
  '/:evidenceId',
  authenticate,
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 100 }),
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
        select: { evidenceId: true },
      });

      if (!evidence) {
        return res.status(404).json(
          buildError(404, 'EVIDENCE_NOT_FOUND', 'Evidence record not found', { evidenceId }, req)
        );
      }

      const claims = await prisma.ownershipClaim.findMany({
        where: { evidenceId },
        orderBy: { createdAt: 'desc' },
        select: {
          ownershipClaimId: true,
          evidenceId: true,
          claimantDeviceId: true,
          claimantPublicKeyReference: true,
          claimType: true,
          claimStatement: true,
          status: true,
          signatureAlgorithm: true,
          createdAt: true,
          updatedAt: true,
          revokedAt: true,
          metadata: true,
        },
      });

      return res.status(200).json({
        evidenceId,
        claimCount: claims.length,
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
// GET /ownership/:evidenceId/claim/:claimId
// Get a specific claim by ID.
// ─────────────────────────────────────────────────────────────────────────────

router.get(
  '/:evidenceId/claim/:claimId',
  authenticate,
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 100 }),
  async (req, res, next) => {
    try {
      const { evidenceId, claimId } = req.params;

      if (!isValidUUID(evidenceId)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'evidenceId must be a valid UUID', null, req)
        );
      }

      if (!claimId || typeof claimId !== 'string') {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'claimId is required', null, req)
        );
      }

      const claim = await prisma.ownershipClaim.findFirst({
        where: {
          ownershipClaimId: claimId,
          evidenceId,
        },
        select: {
          ownershipClaimId: true,
          evidenceId: true,
          claimantDeviceId: true,
          claimantPublicKeyReference: true,
          claimType: true,
          claimStatement: true,
          signatureAlgorithm: true,
          status: true,
          createdAt: true,
          updatedAt: true,
          revokedAt: true,
          metadata: true,
        },
      });

      if (!claim) {
        return res.status(404).json(
          buildError(404, 'CLAIM_NOT_FOUND', 'Claim not found for this evidence', { evidenceId, claimId }, req)
        );
      }

      return res.status(200).json({
        ...claim,
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// PUT /ownership/:evidenceId/claim/:claimId
// Update claim status (admin review).
// Body: { status: "ACCEPTED" | "REJECTED" | "REVOKED" }
// ─────────────────────────────────────────────────────────────────────────────

router.put(
  '/:evidenceId/claim/:claimId',
  authenticate,
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 50 }),
  async (req, res, next) => {
    try {
      const { evidenceId, claimId } = req.params;
      const { status: newStatus } = req.body;

      if (!isValidUUID(evidenceId)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'evidenceId must be a valid UUID', null, req)
        );
      }

      if (!claimId || typeof claimId !== 'string') {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'claimId is required', null, req)
        );
      }

      const validStatuses = ['ACCEPTED', 'REJECTED', 'REVOKED', 'PENDING'];
      if (!newStatus || !validStatuses.includes(newStatus)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', `status must be one of: ${validStatuses.join(', ')}`, null, req)
        );
      }

      // Check claim exists
      const existing = await prisma.ownershipClaim.findFirst({
        where: {
          ownershipClaimId: claimId,
          evidenceId,
        },
      });

      if (!existing) {
        return res.status(404).json(
          buildError(404, 'CLAIM_NOT_FOUND', 'Claim not found for this evidence', { evidenceId, claimId }, req)
        );
      }

      // Build update data
      const updateData = { status: newStatus };
      if (newStatus === 'REVOKED') {
        updateData.revokedAt = new Date();
      }

      const updated = await prisma.ownershipClaim.update({
        where: { ownershipClaimId: claimId },
        data: updateData,
      });

      // Audit log
      await prisma.auditLog.create({
        data: {
          requestId: req.requestId,
          action: 'OWNERSHIP_CLAIM_STATUS_UPDATED',
          entityType: 'OwnershipClaim',
          entityId: claimId,
          actorDeviceId: req.user?.deviceId || null,
          actorKeyId: req.user?.keyId || null,
          ipAddress: req.ip,
          details: {
            evidenceId,
            previousStatus: existing.status,
            newStatus,
          },
        },
      });

      console.info(`[Ownership] Claim updated: claim=${claimId} status=${newStatus}`);

      return res.status(200).json({
        success: true,
        claimId: updated.ownershipClaimId,
        evidenceId: updated.evidenceId,
        status: updated.status,
        previousStatus: existing.status,
        updatedAt: updated.updatedAt,
        revokedAt: updated.revokedAt,
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// DELETE /ownership/:evidenceId/claim/:claimId
// Revoke/withdraw a claim.
// ─────────────────────────────────────────────────────────────────────────────

router.delete(
  '/:evidenceId/claim/:claimId',
  authenticate,
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 20 }),
  async (req, res, next) => {
    try {
      const { evidenceId, claimId } = req.params;

      if (!isValidUUID(evidenceId)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'evidenceId must be a valid UUID', null, req)
        );
      }

      if (!claimId || typeof claimId !== 'string') {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'claimId is required', null, req)
        );
      }

      const claim = await prisma.ownershipClaim.findFirst({
        where: {
          ownershipClaimId: claimId,
          evidenceId,
        },
      });

      if (!claim) {
        return res.status(404).json(
          buildError(404, 'CLAIM_NOT_FOUND', 'Claim not found for this evidence', { evidenceId, claimId }, req)
        );
      }

      // Only claimant or admin can revoke
      const currentUserKeyId = req.user?.keyId || 'anonymous';
      const isClaimant = claim.claimantPublicKeyReference === currentUserKeyId;
      const isAdmin = req.user?.role === 'admin';

      if (!isClaimant && !isAdmin) {
        return res.status(403).json(
          buildError(403, 'FORBIDDEN', 'Only the claimant or an admin can revoke this claim', null, req)
        );
      }

      const updated = await prisma.ownershipClaim.update({
        where: { ownershipClaimId: claimId },
        data: {
          status: 'REVOKED',
          revokedAt: new Date(),
        },
      });

      // Audit log
      await prisma.auditLog.create({
        data: {
          requestId: req.requestId,
          action: 'OWNERSHIP_CLAIM_REVOKED',
          entityType: 'OwnershipClaim',
          entityId: claimId,
          actorDeviceId: req.user?.deviceId || null,
          actorKeyId: currentUserKeyId,
          ipAddress: req.ip,
          details: {
            evidenceId,
            claimantPublicKeyReference: claim.claimantPublicKeyReference,
            revokedBy: currentUserKeyId,
          },
        },
      });

      console.info(`[Ownership] Claim revoked: claim=${claimId} by=${currentUserKeyId}`);

      return res.status(200).json({
        success: true,
        message: 'Claim revoked successfully',
        claimId,
        evidenceId,
        status: updated.status,
        revokedAt: updated.revokedAt,
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

module.exports = router;
