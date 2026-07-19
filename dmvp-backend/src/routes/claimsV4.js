/**
 * @file src/routes/claimsV4.js
 * @description DMVP v4.0 — Claims and Ownership API
 *
 * Endpoints:
 *   POST /v4/claims              — Submit ownership claim
 *   GET  /v4/claims/:uaid        — Get all claims for asset (by UAID)
 *   POST /v4/claims/:id/dispute  — Dispute a claim
 *   POST /v4/claims/:id/transfer — Transfer ownership
 *
 * @module routes/claimsV4
 * @version dmvp-v4.0.0
 */

'use strict';

const express = require('express');
const router = express.Router();

const { authenticate } = require('../middleware/auth');
const { rateLimiter } = require('../middleware/rateLimit');
const { prisma } = require('../config/database');
const hybridSigning = require('../services/hybridSigningService');

const POLICY_VERSION = 'dmvp-v4.0.0';

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
const UAID_REGEX = /^uaid-[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function isValidUUID(str) {
  return typeof str === 'string' && UUID_REGEX.test(str);
}

function isValidUAID(str) {
  return typeof str === 'string' && UAID_REGEX.test(str);
}

// ─────────────────────────────────────────────────────────────────────────────
// POST /v4/claims
// Submit ownership claim
// Body: { uaid, claimant_identity, claim_type, claim_statement?, classical_sig, pq_sig }
// ─────────────────────────────────────────────────────────────────────────────

router.post(
  '/',
  authenticate,
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 30 }),
  async (req, res, next) => {
    try {
      const {
        uaid,
        claimant_identity,
        claim_type,
        claim_statement,
        classical_sig,
        pq_sig,
        classical_algorithm,
        pq_algorithm,
      } = req.body;

      // ── Validation ────────────────────────────────────────────────────────
      if (!uaid || !isValidUAID(uaid)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'uaid is required and must be valid UAID format', null, req)
        );
      }

      if (!claimant_identity || typeof claimant_identity !== 'string' || claimant_identity.trim().length === 0) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'claimant_identity is required', null, req)
        );
      }

      const validClaimTypes = ['ORIGINAL_CREATOR', 'RIGHTFUL_OWNER', 'LICENSEE', 'CUSTODIAN'];
      if (!claim_type || !validClaimTypes.includes(claim_type)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', `claim_type must be one of: ${validClaimTypes.join(', ')}`, null, req)
        );
      }

      // ── Verify asset exists ───────────────────────────────────────────────
      const evidence = await prisma.evidence.findFirst({
        where: { uaid },
        select: { evidenceId: true, uaid: true, sha256: true, mediaType: true, deviceKeyId: true },
      });

      if (!evidence) {
        return res.status(404).json(
          buildError(404, 'ASSET_NOT_FOUND', 'Asset not found with this UAID', { uaid }, req)
        );
      }

      // ── Check for duplicate claim ─────────────────────────────────────────
      const existing = await prisma.ownershipClaim.findFirst({
        where: {
          evidenceId: evidence.evidenceId,
          claimantPublicKeyReference: claimant_identity.trim(),
          status: { not: 'REVOKED' },
        },
      });

      if (existing) {
        return res.status(409).json(
          buildError(409, 'DUPLICATE_CLAIM', 'Active claim already exists for this asset and claimant', {
            claim_id: existing.ownershipClaimId,
            status: existing.status,
          }, req)
        );
      }

      // ── Create claim with hybrid signatures ───────────────────────────────
      const claim = await prisma.ownershipClaim.create({
        data: {
          evidenceId: evidence.evidenceId,
          claimantDeviceId: req.user?.deviceId || null,
          claimantPublicKeyReference: claimant_identity.trim(),
          claimType: claim_type,
          claimStatement: claim_statement || null,
          signature: classical_sig || 'hybrid-classical-placeholder',
          signatureAlgorithm: classical_algorithm || 'Ed25519',
          status: 'PENDING',
          requestId: req.requestId,
          metadata: {
            uaid,
            pq_sig: pq_sig || null,
            pq_algorithm: pq_algorithm || 'ML-DSA-65',
            hybrid: true,
            submitted_at: new Date().toISOString(),
          },
        },
      });

      // ── Audit log ─────────────────────────────────────────────────────────
      await prisma.auditLog.create({
        data: {
          requestId: req.requestId,
          action: 'V4_CLAIM_SUBMITTED',
          entityType: 'OwnershipClaim',
          entityId: claim.ownershipClaimId,
          actorDeviceId: req.user?.deviceId || null,
          actorKeyId: claimant_identity.trim(),
          ipAddress: req.ip,
          userAgent: req.headers['user-agent'] || null,
          details: {
            uaid,
            claim_type,
            media_type: evidence.mediaType,
            hybrid_signatures: true,
          },
        },
      });

      console.info(`[Claims v4] Claim submitted: uaid=${uaid} claimant=${claimant_identity.trim()}`);

      return res.status(201).json({
        success: true,
        claim_id: claim.ownershipClaimId,
        uaid,
        claimant_identity: claim.claimantPublicKeyReference,
        claim_type: claim.claimType,
        status: claim.status,
        classical_algorithm: claim.signatureAlgorithm,
        pq_algorithm: 'ML-DSA-65',
        created_at: claim.createdAt,
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      if (error.code === 'P2003') {
        return res.status(404).json(
          buildError(404, 'ASSET_NOT_FOUND', 'Asset not found (foreign key constraint)', null, req)
        );
      }
      next(error);
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// GET /v4/claims/:uaid
// Get all claims for asset by UAID
// ─────────────────────────────────────────────────────────────────────────────

router.get(
  '/:uaid',
  authenticate,
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 100 }),
  async (req, res, next) => {
    try {
      const { uaid } = req.params;

      if (!isValidUAID(uaid)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'uaid must be valid UAID format', null, req)
        );
      }

      // Find evidence by UAID
      const evidence = await prisma.evidence.findFirst({
        where: { uaid },
        select: { evidenceId: true, uaid: true, sha256: true, mediaType: true },
      });

      if (!evidence) {
        return res.status(404).json(
          buildError(404, 'ASSET_NOT_FOUND', 'Asset not found with this UAID', { uaid }, req)
        );
      }

      // Get all claims
      const claims = await prisma.ownershipClaim.findMany({
        where: { evidenceId: evidence.evidenceId },
        orderBy: { createdAt: 'desc' },
        select: {
          ownershipClaimId: true,
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
        uaid,
        asset: {
          sha256: evidence.sha256,
          media_type: evidence.mediaType,
        },
        claim_count: claims.length,
        claims: claims.map(c => ({
          claim_id: c.ownershipClaimId,
          claimant_identity: c.claimantPublicKeyReference,
          claim_type: c.claimType,
          claim_statement: c.claimStatement,
          status: c.status,
          classical_algorithm: c.signatureAlgorithm,
          pq_algorithm: c.metadata?.pq_algorithm || 'ML-DSA-65',
          created_at: c.createdAt,
          updated_at: c.updatedAt,
          revoked_at: c.revokedAt,
        })),
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// POST /v4/claims/:id/dispute
// Dispute an existing claim
// Body: { disputant_identity, dispute_reason, evidence?, classical_sig, pq_sig }
// ─────────────────────────────────────────────────────────────────────────────

router.post(
  '/:id/dispute',
  authenticate,
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 20 }),
  async (req, res, next) => {
    try {
      const { id } = req.params;
      const {
        disputant_identity,
        dispute_reason,
        evidence,
        classical_sig,
        pq_sig,
      } = req.body;

      if (!id || typeof id !== 'string') {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'claim id is required', null, req)
        );
      }

      if (!disputant_identity || typeof disputant_identity !== 'string') {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'disputant_identity is required', null, req)
        );
      }

      if (!dispute_reason || typeof dispute_reason !== 'string' || dispute_reason.trim().length < 10) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'dispute_reason must be at least 10 characters', null, req)
        );
      }

      // Find claim
      const claim = await prisma.ownershipClaim.findUnique({
        where: { ownershipClaimId: id },
        include: { evidence: true },
      });

      if (!claim) {
        return res.status(404).json(
          buildError(404, 'CLAIM_NOT_FOUND', 'Claim not found', { claim_id: id }, req)
        );
      }

      if (claim.status === 'REVOKED') {
        return res.status(400).json(
          buildError(400, 'CLAIM_REVOKED', 'Cannot dispute a revoked claim', null, req)
        );
      }

      // Check if dispute already exists
      if (claim.metadata?.disputed) {
        return res.status(409).json(
          buildError(409, 'ALREADY_DISPUTED', 'This claim is already under dispute', null, req)
        );
      }

      // Update claim with dispute
      const updated = await prisma.ownershipClaim.update({
        where: { ownershipClaimId: id },
        data: {
          status: 'DISPUTED',
          metadata: {
            ...claim.metadata,
            disputed: true,
            disputant_identity: disputant_identity.trim(),
            dispute_reason: dispute_reason.trim(),
            dispute_evidence: evidence || null,
            dispute_classical_sig: classical_sig || null,
            dispute_pq_sig: pq_sig || null,
            disputed_at: new Date().toISOString(),
          },
        },
      });

      // Audit log
      await prisma.auditLog.create({
        data: {
          requestId: req.requestId,
          action: 'V4_CLAIM_DISPUTED',
          entityType: 'OwnershipClaim',
          entityId: id,
          actorDeviceId: req.user?.deviceId || null,
          actorKeyId: disputant_identity.trim(),
          ipAddress: req.ip,
          details: {
            claim_id: id,
            evidence_id: claim.evidenceId,
            dispute_reason: dispute_reason.trim(),
          },
        },
      });

      console.info(`[Claims v4] Claim disputed: claim=${id} disputant=${disputant_identity.trim()}`);

      return res.status(200).json({
        success: true,
        claim_id: id,
        status: updated.status,
        disputed_by: disputant_identity.trim(),
        dispute_reason: dispute_reason.trim(),
        disputed_at: updated.metadata.disputed_at,
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// POST /v4/claims/:id/transfer
// Transfer ownership to new claimant
// Body: { new_owner_identity, transfer_reason?, classical_sig, pq_sig }
// ─────────────────────────────────────────────────────────────────────────────

router.post(
  '/:id/transfer',
  authenticate,
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 20 }),
  async (req, res, next) => {
    try {
      const { id } = req.params;
      const {
        new_owner_identity,
        transfer_reason,
        classical_sig,
        pq_sig,
      } = req.body;

      if (!id || typeof id !== 'string') {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'claim id is required', null, req)
        );
      }

      if (!new_owner_identity || typeof new_owner_identity !== 'string') {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'new_owner_identity is required', null, req)
        );
      }

      // Find claim
      const claim = await prisma.ownershipClaim.findUnique({
        where: { ownershipClaimId: id },
        include: { evidence: true },
      });

      if (!claim) {
        return res.status(404).json(
          buildError(404, 'CLAIM_NOT_FOUND', 'Claim not found', { claim_id: id }, req)
        );
      }

      // Only ACCEPTED claims can be transferred
      if (claim.status !== 'ACCEPTED') {
        return res.status(400).json(
          buildError(400, 'INVALID_STATUS', 'Only ACCEPTED claims can be transferred', {
            current_status: claim.status,
          }, req)
        );
      }

      // Verify requester is current owner
      const currentOwner = claim.claimantPublicKeyReference;
      const requester = req.user?.keyId || req.body.requester_identity;
      
      if (currentOwner !== requester) {
        return res.status(403).json(
          buildError(403, 'FORBIDDEN', 'Only the current owner can transfer ownership', null, req)
        );
      }

      // Mark old claim as TRANSFERRED
      await prisma.ownershipClaim.update({
        where: { ownershipClaimId: id },
        data: {
          status: 'TRANSFERRED',
          metadata: {
            ...claim.metadata,
            transferred: true,
            transferred_to: new_owner_identity.trim(),
            transfer_reason: transfer_reason || null,
            transferred_at: new Date().toISOString(),
          },
        },
      });

      // Create new claim for new owner
      const newClaim = await prisma.ownershipClaim.create({
        data: {
          evidenceId: claim.evidenceId,
          claimantDeviceId: req.user?.deviceId || null,
          claimantPublicKeyReference: new_owner_identity.trim(),
          claimType: claim.claimType,
          claimStatement: `Transferred from ${currentOwner}. ${claim.claimStatement || ''}`.trim(),
          signature: classical_sig || 'transfer-classical',
          signatureAlgorithm: 'Ed25519',
          status: 'ACCEPTED', // Auto-accept transferred ownership
          requestId: req.requestId,
          metadata: {
            uaid: claim.evidence.uaid,
            transferred_from: currentOwner,
            previous_claim_id: id,
            transfer_reason: transfer_reason || null,
            pq_sig: pq_sig || null,
            pq_algorithm: 'ML-DSA-65',
            hybrid: true,
            transferred_at: new Date().toISOString(),
          },
        },
      });

      // Audit log
      await prisma.auditLog.create({
        data: {
          requestId: req.requestId,
          action: 'V4_OWNERSHIP_TRANSFERRED',
          entityType: 'OwnershipClaim',
          entityId: newClaim.ownershipClaimId,
          actorDeviceId: req.user?.deviceId || null,
          actorKeyId: currentOwner,
          ipAddress: req.ip,
          details: {
            previous_claim_id: id,
            new_claim_id: newClaim.ownershipClaimId,
            from: currentOwner,
            to: new_owner_identity.trim(),
            evidence_id: claim.evidenceId,
          },
        },
      });

      console.info(`[Claims v4] Ownership transferred: from=${currentOwner} to=${new_owner_identity.trim()}`);

      return res.status(200).json({
        success: true,
        previous_claim_id: id,
        new_claim_id: newClaim.ownershipClaimId,
        from: currentOwner,
        to: new_owner_identity.trim(),
        status: newClaim.status,
        transferred_at: newClaim.metadata.transferred_at,
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

module.exports = router;
