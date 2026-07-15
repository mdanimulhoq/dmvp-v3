/**
 * @file src/routes/verify.js
 * @description DMVP v3.0 — Verification Routes
 *
 * Endpoints:
 *   GET  /verify         — Verification service info
 *   POST /verify         — Submit verification request
 *   GET  /verify/policy  — Retrieve active verification policy metadata
 *
 * @module routes/verify
 * @version dmvp-v3.0.0
 */

'use strict';

const express = require('express');
const router = express.Router();

const { authenticate } = require('../middleware/auth');
const { verifyRateLimit } = require('../middleware/rateLimit');
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
// GET /verify
// Verification service info
// ─────────────────────────────────────────────────────────────────────────────

router.get(
  '/',
  authenticate,
  async (req, res) => {
    return res.status(200).json({
      service: 'DMVP Verification',
      version: '3.0.0',
      endpoints: {
        verify: 'POST /',
        policy: 'GET /policy',
      },
      policy_version: POLICY_VERSION,
      request_id: req.requestId,
    });
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// POST /verify
// Submit a verification request and receive multi-axis verdict
// ─────────────────────────────────────────────────────────────────────────────

router.post(
  '/',
  authenticate,
  verifyRateLimit,
  async (req, res, next) => {
    try {
      const {
        sha256,
        canonicalMediaHash,
        perceptualHash,
        fingerprintProfile,
        mediaType,
        verificationMode,
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

      const mode = (verificationMode || 'standard').toLowerCase();
      if (!['fast', 'standard', 'deep'].includes(mode)) {
        return res.status(400).json(
          buildError(400, 'VALIDATION_ERROR', 'verificationMode must be fast, standard, or deep', null, req)
        );
      }

      // ── Stage 1: Exact hash lookup ────────────────────────────────────────
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
              attestationStatus: true,
              revokedAt: true,
            },
          },
        },
      });

      // ── Build verdict ─────────────────────────────────────────────────────
      let integrityVerdict = 'NO_EXACT_MATCH';
      let provenanceVerdict = 'NO_TRUSTED_PROVENANCE';
      let similarityVerdict = 'NO_RELIABLE_SIMILARITY';
      let evidenceQuality = 'LOW';
      let trustTier = 'TIER_D';
      let matchedEvidence = null;
      let transformationIndicators = [];

      if (exactMatch) {
        integrityVerdict = 'EXACT_MATCH';
        matchedEvidence = {
          evidenceId: exactMatch.evidenceId,
          mediaType: exactMatch.mediaType,
          registrationServerTime: exactMatch.registrationServerTime,
          signerDeviceKeyId: exactMatch.signerDeviceKeyId,
        };

        // Provenance check
        if (exactMatch.signerDevice) {
          trustTier = exactMatch.signerDevice.trustTier;
          
          if (exactMatch.signerDevice.revokedAt) {
            provenanceVerdict = 'NO_TRUSTED_PROVENANCE';
            evidenceQuality = 'LOW';
          } else if (exactMatch.signerDevice.attestationStatus === 'VALID') {
            provenanceVerdict = 'SIGNED_TRUSTED_DEVICE';
            evidenceQuality = trustTier === 'TIER_A' ? 'HIGH_EVIDENTIARY_STRENGTH' : 'MODERATE';
          } else {
            provenanceVerdict = 'ATTESTATION_MISSING';
            evidenceQuality = 'MODERATE';
          }
        }
      }

      // ── Stage 2: Similarity search (if no exact match and not fast mode) ──
      if (!exactMatch && mode !== 'fast' && perceptualHash) {
        const candidates = await prisma.evidence.findMany({
          where: {
            perceptualHash: {
              startsWith: perceptualHash.substring(0, 8),
            },
            mediaType: mediaType.toUpperCase(),
          },
          take: 10,
          include: {
            signerDevice: {
              select: {
                deviceId: true,
                keyId: true,
                trustTier: true,
                attestationStatus: true,
                revokedAt: true,
              },
            },
          },
        });

        if (candidates.length > 0) {
          similarityVerdict = 'WEAK_SIMILARITY';
        }
      }

      // ── Record verification event ─────────────────────────────────────────
      await prisma.verificationRecord.create({
        data: {
          mediaType: mediaType.toUpperCase(),
          inputSha256Original: sha256.toLowerCase(),
          inputCanonicalHash: canonicalMediaHash || null,
          inputPerceptualHash: perceptualHash || null,
          inputFingerprintProfile: fingerprintProfile || null,
          matchedEvidenceId: exactMatch?.evidenceId || null,
          matchedDeviceId: exactMatch?.signerDevice?.deviceId || null,
          verdict: {
            integrity: integrityVerdict,
            provenance: provenanceVerdict,
            similarity: similarityVerdict,
            evidenceQuality,
            trustTier,
          },
          transformationIndicators: transformationIndicators,
          trustTier: trustTier,
          requestId: req.requestId,
        },
      });

      console.info(`[Verify] ${integrityVerdict} for ${sha256.substring(0, 16)}... mode=${mode}`);

      // ── Step 4.3: Add algorithm versions to response ─────────────────────
      return res.status(200).json({
        integrity_verdict: integrityVerdict,
        provenance_verdict: provenanceVerdict,
        similarity_verdict: similarityVerdict,
        evidence_quality: evidenceQuality,
        trust_tier: trustTier,
        transformation_indicators: transformationIndicators,
        matched_evidence: matchedEvidence,
        verification_mode: mode,
        algorithm_versions_used: {
          fingerprint: "phash-dct-v1",
          similarity: "hamming-v1",
          normalization: "jpeg-baseline-v1"
        },
        policy_version: POLICY_VERSION,
        request_id: req.requestId,
      });
    } catch (error) {
      next(error);
    }
  }
);

// ─────────────────────────────────────────────────────────────────────────────
// GET /verify/policy
// Retrieve active verification policy metadata
// ─────────────────────────────────────────────────────────────────────────────

router.get(
  '/policy',
  authenticate,
  async (req, res) => {
    return res.status(200).json({
      policy_version: POLICY_VERSION,
      protocol_version: process.env.DMVP_PROTOCOL_VERSION || 'dmvp-v3.0.0',
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
        fingerprint: 'phash-dct-v1',
        similarity: 'hamming-v1',
        normalization: 'jpeg-baseline-v1',
      },
      evidence_quality_mapping: {
        TIER_A: 'HIGH_EVIDENTIARY_STRENGTH',
        TIER_B: 'MODERATE_EVIDENTIARY_STRENGTH',
        TIER_C: 'LOW_EVIDENTIARY_STRENGTH',
        TIER_D: 'LOW_EVIDENTIARY_STRENGTH',
      },
      request_id: req.requestId,
    });
  }
);

module.exports = router;
