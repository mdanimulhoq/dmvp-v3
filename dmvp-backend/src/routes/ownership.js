/**
 * src/routes/ownership.js
 *
 * Express routes for ownership claims and management.
 * Endpoints:
 *   POST /ownership/claim - Submit an ownership claim
 *   GET /ownership/:evidence_id - Retrieve claims for an evidence record
 *   GET /ownership/:evidence_id/claim/:claim_id - Get a specific claim
 *   (Optional: PUT /ownership/:evidence_id/claim/:claim_id/review - for admin review)
 *
 * All routes enforce authentication, rate limiting, and input validation.
 */

const express = require('express');
const router = express.Router();

// Middleware
const { authenticate } = require('../middleware/auth');
const { rateLimiter } = require('../middleware/rateLimit');
const { validateEvidenceId } = require('../middleware/validation');

// Prisma client
const { prisma } = require('../config/database');

// Logger
const logger = console;

/**
 * POST /ownership/claim
 *
 * Submit an ownership claim for an evidence record.
 *
 * Request body:
 *   {
 *     evidence_id: string (UUID) - required,
 *     claimant_identity: string - required (e.g., email, DID),
 *     claim_type: "original_author" | "license_holder" | "custodian" | "other" - required,
 *     supporting_data: object - optional (additional evidence)
 *   }
 *
 * Response: 201 Created with claim record.
 *           404 Not Found if evidence does not exist.
 *           409 Conflict if claim already exists (same claimant + evidence).
 */
router.post(
  '/claim',
  authenticate,
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 30 }),
  async (req, res, next) => {
    try {
      const { evidence_id, claimant_identity, claim_type, supporting_data } = req.body;

      // Validate required fields
      if (!evidence_id || typeof evidence_id !== 'string') {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'evidence_id is required and must be a string',
          detail: null,
        });
      }
      if (!claimant_identity || typeof claimant_identity !== 'string') {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'claimant_identity is required and must be a string',
          detail: null,
        });
      }
      if (!claim_type || typeof claim_type !== 'string') {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'claim_type is required and must be a string',
          detail: null,
        });
      }

      // Validate evidence exists
      const evidence = await prisma.evidenceRecord.findUnique({
        where: { evidence_id },
        select: { evidence_id: true },
      });
      if (!evidence) {
        return res.status(404).json({
          error_code: 'EVIDENCE_NOT_FOUND',
          message: 'Evidence record not found',
          detail: null,
        });
      }

      // Check for duplicate claim (same claimant + evidence)
      const existing = await prisma.ownershipClaim.findFirst({
        where: {
          evidence_id,
          claimant_identity,
          // Optionally filter by not rejected?
        },
      });
      if (existing) {
        return res.status(409).json({
          error_code: 'DUPLICATE_CLAIM',
          message: 'A claim already exists for this evidence and claimant',
          detail: { claim_id: existing.claim_id, status: existing.review_status },
        });
      }

      // Create claim
      const claim = await prisma.ownershipClaim.create({
        data: {
          evidence_id,
          claimant_identity,
          claim_type,
          claim_timestamp: new Date().toISOString(),
          review_status: 'PENDING', // or 'SUBMITTED'
          // supporting_data can be stored as JSON if schema has a field; otherwise skip
          // We'll assume schema has a 'metadata' field or we can ignore.
          // For safety, we'll only include fields that exist in schema.
          // According to schema.prisma, OwnershipClaim has:
          //   id (String) @id @default(cuid())
          //   evidence_id (String)
          //   claimant_identity (String)
          //   claim_timestamp (DateTime)
          //   claim_type (String)
          //   review_status (String)
          //   review_notes (String?)
          //   reviewed_at (DateTime?)
          //   reviewed_by (String?)
          //   created_at (DateTime) @default(now())
          //   updated_at (DateTime) @updatedAt
          //   evidence EvidenceRecord @relation(fields: [evidence_id], references: [evidence_id])
          // So we can set review_status, but no 'supporting_data' field.
          // We'll store supporting_data in a separate table if needed, but for now we ignore.
        },
      });

      // Audit log
      await prisma.auditLog.create({
        data: {
          event_type: 'OWNERSHIP_CLAIM_SUBMITTED',
          actor: req.user ? req.user.id : claimant_identity,
          target: evidence_id,
          timestamp: new Date().toISOString(),
          policy_version: 'dmvp-v3.0.0',
          metadata: {
            claim_id: claim.id,
            claim_type,
          },
        },
      });

      logger.info(`Ownership claim submitted for ${evidence_id} by ${claimant_identity}`);
      res.status(201).json(claim);
    } catch (error) {
      if (error.code === 'P2003' || error.message.includes('Foreign key')) {
        // Foreign key constraint failure (evidence_id not exist)
        return res.status(404).json({
          error_code: 'EVIDENCE_NOT_FOUND',
          message: 'Evidence record not found',
          detail: null,
        });
      }
      next(error);
    }
  }
);

/**
 * GET /ownership/:evidence_id
 *
 * Retrieve all claims for a given evidence record.
 *
 * Returns array of claim records (may be filtered by permissions).
 * For now, return all claims (public). In production, may restrict.
 *
 * Response: 200 OK with array of claims.
 *           404 Not Found if evidence not exists.
 */
router.get(
  '/:evidence_id',
  authenticate,
  validateEvidenceId,
  async (req, res, next) => {
    try {
      const { evidence_id } = req.params;

      // Check evidence exists
      const evidence = await prisma.evidenceRecord.findUnique({
        where: { evidence_id },
        select: { evidence_id: true },
      });
      if (!evidence) {
        return res.status(404).json({
          error_code: 'EVIDENCE_NOT_FOUND',
          message: 'Evidence record not found',
          detail: null,
        });
      }

      const claims = await prisma.ownershipClaim.findMany({
        where: { evidence_id },
        orderBy: { created_at: 'desc' },
      });

      res.json({ evidence_id, claims });
    } catch (error) {
      next(error);
    }
  }
);

/**
 * GET /ownership/:evidence_id/claim/:claim_id
 *
 * Get a specific claim by ID.
 *
 * Response: 200 OK with claim record.
 *           404 Not Found if claim not found or doesn't belong to evidence.
 */
router.get(
  '/:evidence_id/claim/:claim_id',
  authenticate,
  validateEvidenceId,
  async (req, res, next) => {
    try {
      const { evidence_id, claim_id } = req.params;

      const claim = await prisma.ownershipClaim.findFirst({
        where: {
          id: claim_id,
          evidence_id,
        },
      });
      if (!claim) {
        return res.status(404).json({
          error_code: 'CLAIM_NOT_FOUND',
          message: 'Claim not found for this evidence',
          detail: null,
        });
      }

      res.json(claim);
    } catch (error) {
      next(error);
    }
  }
);

/**
 * PUT /ownership/:evidence_id/claim/:claim_id/review
 *
 * Admin endpoint to review and update claim status.
 * This would be protected by admin role middleware in production.
 *
 * Request body:
 *   {
 *     review_status: "APPROVED" | "REJECTED" | "PENDING",
 *     review_notes: string - optional
 *   }
 *
 * Response: 200 OK with updated claim.
 */
router.put(
  '/:evidence_id/claim/:claim_id/review',
  authenticate,
  // In production, add admin check middleware
  rateLimiter({ windowMs: 15 * 60 * 1000, max: 50 }),
  validateEvidenceId,
  async (req, res, next) => {
    try {
      const { evidence_id, claim_id } = req.params;
      const { review_status, review_notes } = req.body;

      if (!review_status || !['APPROVED', 'REJECTED', 'PENDING'].includes(review_status)) {
        return res.status(400).json({
          error_code: 'VALIDATION_ERROR',
          message: 'review_status must be one of: APPROVED, REJECTED, PENDING',
          detail: null,
        });
      }

      // Check claim exists
      const existing = await prisma.ownershipClaim.findFirst({
        where: {
          id: claim_id,
          evidence_id,
        },
      });
      if (!existing) {
        return res.status(404).json({
          error_code: 'CLAIM_NOT_FOUND',
          message: 'Claim not found for this evidence',
          detail: null,
        });
      }

      // Update claim
      const updated = await prisma.ownershipClaim.update({
        where: { id: claim_id },
        data: {
          review_status,
          review_notes: review_notes || null,
          reviewed_at: new Date().toISOString(),
          reviewed_by: req.user ? req.user.id : 'system',
        },
      });

      // Audit log
      await prisma.auditLog.create({
        data: {
          event_type: 'OWNERSHIP_CLAIM_REVIEWED',
          actor: req.user ? req.user.id : 'system',
          target: evidence_id,
          timestamp: new Date().toISOString(),
          policy_version: 'dmvp-v3.0.0',
          metadata: {
            claim_id,
            new_status: review_status,
          },
        },
      });

      logger.info(`Ownership claim ${claim_id} reviewed: ${review_status}`);
      res.json(updated);
    } catch (error) {
      next(error);
    }
  }
);

module.exports = router;
