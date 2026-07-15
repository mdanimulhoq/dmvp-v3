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

// ── Step 4.4: Hamming distance comparison ──────────────────────────────

function hammingDistance(hashA, hashB) {
  if (typeof hashA !== 'string' || typeof hashB !== 'string') {
    throw new TypeError('Both arguments must be strings');
  }
  if (hashA.length !== hashB.length) {
    throw new Error('Hash strings must be of equal length');
  }

  let distance = 0;
  const isHex = /^[0-9a-fA-F]+$/.test(hashA) && /^[0-9a-fA-F]+$/.test(hashB);

  if (isHex && hashA.length % 2 === 0 && hashB.length % 2 === 0) {
    const bufA = Buffer.from(hashA, 'hex');
    const bufB = Buffer.from(hashB, 'hex');
    for (let i = 0; i < bufA.length; i++) {
      const xor = bufA[i] ^ bufB[i];
      distance += popCount(xor);
    }
  } else {
    for (let i = 0; i < hashA.length; i++) {
      if (hashA[i] !== hashB[i]) distance++;
    }
  }
  return distance;
}

function popCount(byte) {
  let count = 0;
  while (byte) {
    count += byte & 1;
    byte >>= 1;
  }
  return count;
}

function compareFingerprintProfiles(profileA, profileB, weights = null) {
  if (!profileA || typeof profileA !== 'object') {
    throw new TypeError('profileA must be an object');
  }
  if (!profileB || typeof profileB !== 'object') {
    throw new TypeError('profileB must be an object');
  }

  const defaultWeights = { phash: 0.5, dhash: 0.3, blockHash: 0.2 };
  const w = weights || defaultWeights;

  const totalWeight = Object.values(w).reduce((sum, val) => sum + val, 0);
  if (Math.abs(totalWeight - 1.0) > 1e-9) {
    throw new Error('Weights must sum to 1');
  }

  const fields = ['phash', 'dhash', 'blockHash'];
  let weightedSimilarity = 0;
  let usedWeight = 0;

  for (const field of fields) {
    if (profileA[field] && profileB[field] && w[field] && w[field] > 0) {
      const hashA = profileA[field];
      const hashB = profileB[field];
      if (typeof hashA === 'string' && typeof hashB === 'string' && hashA.length === hashB.length) {
        let distance;
        try {
          distance = hammingDistance(hashA, hashB);
        } catch (err) {
          continue;
        }
        const bitLength = hashA.length * (isHexString(hashA) ? 4 : 1);
        const similarity = Math.max(0, 1 - (distance / bitLength));
        weightedSimilarity += w[field] * similarity;
        usedWeight += w[field];
      }
    }
  }

  if (usedWeight === 0) return 0.0;
  return Math.min(1, Math.max(0, weightedSimilarity / usedWeight));
}

function isHexString(str) {
  return /^[0-9a-fA-F]+$/.test(str);
}

// ── Step 4.7: Transformation detection ──────────────────────────────────

function inferTransformations(profileA, profileB, durationA, durationB, mediaType) {
  const indicators = [];

  // Compression detection: if blockHash similar but phash differs
  if (profileA.phash && profileB.phash && profileA.blockHash && profileB.blockHash) {
    try {
      const phashDist = hammingDistance(profileA.phash, profileB.phash);
      const blockDist = hammingDistance(profileA.blockHash, profileB.blockHash);
      const bitLen = profileA.phash.length * 4;
      const phashSim = 1 - (phashDist / bitLen);
      const blockSim = 1 - (blockDist / (profileA.blockHash.length * 4));
      if (phashSim < 0.7 && blockSim > 0.8) {
        indicators.push('crop_resize');
      }
      if (phashSim < 0.6 && blockSim < 0.6) {
        indicators.push('compression_detected');
      }
    } catch (_) { /* skip */ }
  }

  // Trim detection: duration mismatch
  if (durationA && durationB && durationA > 0 && durationB > 0) {
    const ratio = Math.min(durationA, durationB) / Math.max(durationA, durationB);
    if (ratio < 0.8) {
      indicators.push('trim_likely');
    }
  }

  // Frame rate change (if available)
  if (profileA.fps && profileB.fps && profileA.fps > 0 && profileB.fps > 0) {
    const fpsRatio = Math.min(profileA.fps, profileB.fps) / Math.max(profileA.fps, profileB.fps);
    if (fpsRatio < 0.9) {
      indicators.push('frame_rate_change');
    }
  }

  // ── Step 4.7: Crop/resize detection from aspect ratio ──
  if (mediaType && mediaType.toUpperCase() === 'IMAGE') {
    const origAspect = (profileA.width && profileA.height) 
      ? profileA.width / profileA.height 
      : null;
    const matchedAspect = (profileB.width && profileB.height) 
      ? profileB.width / profileB.height 
      : null;
    
    if (origAspect !== null && matchedAspect !== null && 
        Math.abs(origAspect - matchedAspect) > 0.1) {
      indicators.push('crop_resize');
    }
  }

  // ── Step 4.7: Codec change = transcode ──
  if (profileA.codec && profileB.codec && 
      profileA.codec !== profileB.codec) {
    indicators.push('transcode_likely');
  }

  return indicators;
}

// ── Step 4.6: Evidence Quality Composite Score ──────────────────────────

function calculateEvidenceQuality(trustTier, hasTimestamp, isAlgoCurrent) {
  // Revoked device always LOW
  if (trustTier === 'TIER_D') {
    return 'LOW_EVIDENTIARY_STRENGTH';
  }

  // Trust tier score: A=3, B=2, C=1, D=0
  const tierScore = {
    'TIER_A': 3,
    'TIER_B': 2,
    'TIER_C': 1,
    'TIER_D': 0
  }[trustTier] || 0;

  // Timestamp score: trusted=2, server-only=1, none=0
  const timestampScore = hasTimestamp === 'trusted' ? 2 : hasTimestamp === 'server' ? 1 : 0;

  // Algorithm freshness score: current=1, outdated=0
  const algoScore = isAlgoCurrent ? 1 : 0;

  const totalScore = tierScore + timestampScore + algoScore;

  // Max score = 6 (3+2+1)
  if (totalScore >= 5) {
    return 'HIGH_EVIDENTIARY_STRENGTH';
  } else if (totalScore >= 3) {
    return 'MODERATE_EVIDENTIARY_STRENGTH';
  } else {
    return 'LOW_EVIDENTIARY_STRENGTH';
  }
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
      let matchedEvidenceList = [];

      if (exactMatch) {
        integrityVerdict = 'EXACT_MATCH';
        matchedEvidence = {
          evidenceId: exactMatch.evidenceId,
          mediaType: exactMatch.mediaType,
          registrationServerTime: exactMatch.registrationServerTime,
          signerDeviceKeyId: exactMatch.signerDeviceKeyId,
        };
        matchedEvidenceList.push({
          evidenceId: exactMatch.evidenceId,
          sha256: exactMatch.sha256Original,
          matchType: 'exact',
          similarityScore: 1.0,
          timestamp: exactMatch.createdAt,
        });

        // ── Step 4.6: Provenance + Evidence Quality Composite Score ──────
        if (exactMatch.signerDevice) {
          trustTier = exactMatch.signerDevice.trustTier;

          if (exactMatch.signerDevice.revokedAt) {
            provenanceVerdict = 'NO_TRUSTED_PROVENANCE';
            evidenceQuality = 'LOW_EVIDENTIARY_STRENGTH';
          } else if (exactMatch.signerDevice.attestationStatus === 'VALID') {
            provenanceVerdict = 'SIGNED_TRUSTED_DEVICE';
          } else {
            provenanceVerdict = 'ATTESTATION_MISSING';
          }

          // Evidence Quality Composite Score
          let qualityScore = 0;
          const tierScores = { TIER_A: 3, TIER_B: 2, TIER_C: 1, TIER_D: 0 };
          qualityScore += tierScores[trustTier] || 0;

          if (exactMatch.trustedTimestampTokenReference) {
            qualityScore += 2;
          } else if (exactMatch.registrationServerTime) {
            qualityScore += 1;
          }

          const algoV = exactMatch.fingerprintAlgorithmVersions || {};
          if (algoV.fingerprint === 'phash-dct-v1') {
            qualityScore += 1;
          }

          if (qualityScore >= 5) {
            evidenceQuality = 'HIGH_EVIDENTIARY_STRENGTH';
          } else if (qualityScore >= 3) {
            evidenceQuality = 'MODERATE_EVIDENTIARY_STRENGTH';
          } else {
            evidenceQuality = 'LOW_EVIDENTIARY_STRENGTH';
          }

          // Revoked always overrides to LOW
          if (exactMatch.signerDevice.revokedAt) {
            evidenceQuality = 'LOW_EVIDENTIARY_STRENGTH';
          }
        }
      }

      // ── Stage 2: Similarity search ──────────────────────────────────────
      let similarityScore = 0;
      let similarCandidates = [];

      if (fingerprintProfile && Object.keys(fingerprintProfile).length > 0 && !exactMatch) {
        const candidates = await prisma.evidence.findMany({
          where: {
            mediaType: mediaType.toUpperCase(),
            status: 'ACTIVE',
          },
          take: 100,
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

        for (const candidate of candidates) {
          const storedProfile = candidate.robustFingerprintProfile;
          if (!storedProfile) continue;

          try {
            const score = compareFingerprintProfiles(
              fingerprintProfile,
              storedProfile
            );
            if (score > 0.3) {
              similarCandidates.push({
                ...candidate,
                similarityScore: score,
              });
            }
          } catch (_) { /* skip */ }
        }

        similarCandidates.sort((a, b) => b.similarityScore - a.similarityScore);
        const topMatches = similarCandidates.slice(0, 5);

        if (topMatches.length > 0) {
          similarityScore = topMatches[0].similarityScore;

          // Determine similarity verdict
          if (similarityScore >= 0.95) {
            similarityVerdict = 'STRONG_DERIVATIVE';
          } else if (similarityScore >= 0.80) {
            similarityVerdict = 'PROBABLE_DERIVATIVE';
          } else if (similarityScore >= 0.50) {
            similarityVerdict = 'WEAK_SIMILARITY';
          }

          // Add to matched evidence list (avoid duplicates)
          const existingIds = new Set(matchedEvidenceList.map(m => m.evidenceId));
          for (const match of topMatches) {
            if (!existingIds.has(match.evidenceId)) {
              matchedEvidenceList.push({
                evidenceId: match.evidenceId,
                sha256: match.sha256Original,
                matchType: 'similarity',
                similarityScore: match.similarityScore,
                timestamp: match.createdAt,
              });
              existingIds.add(match.evidenceId);
            }
          }

          // ── Step 4.7: Transformation detection ────────────────────────
          if (topMatches.length > 0 && fingerprintProfile) {
            const bestMatch = topMatches[0];
            const durationA = fingerprintProfile.durationMs || null;
            const durationB = bestMatch.robustFingerprintProfile?.durationMs || null;
            
            transformationIndicators = inferTransformations(
              fingerprintProfile,
              bestMatch.robustFingerprintProfile || {},
              durationA,
              durationB,
              mediaType
            );
          }
        }
      }

      // ── Step 7.4: Build warnings array ──────────────────────────────────
      const warnings = [];

      // Degraded timestamp mode
      if (exactMatch && !exactMatch.trustedTimestampTokenReference) {
        warnings.push('timestamp_degraded');
      }

      // Software-only key (Tier C)
      if (trustTier === 'TIER_C') {
        warnings.push('software_only_key');
      }

      // Old algorithm version
      const algoVersions = exactMatch?.fingerprintAlgorithmVersions || {};
      if (algoVersions.fingerprint && algoVersions.fingerprint !== 'phash-dct-v1') {
        warnings.push('old_algorithm_version');
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

      // ── Step 4.3 + 4.6 + 4.7 + 7.4: Response ────────────────────────────────
      return res.status(200).json({
        integrity_verdict: integrityVerdict,
        provenance_verdict: provenanceVerdict,
        similarity_verdict: similarityVerdict,
        evidence_quality: evidenceQuality,
        trust_tier: trustTier,
        transformation_indicators: transformationIndicators,
        matched_evidence_list: matchedEvidenceList,
        warnings,  // ── Step 7.4: Warnings added ──
        verification_mode: mode,
        algorithm_versions_used: {
          fingerprint: "phash-dct-v1",
          similarity: "hamming-v1",
          normalization: "jpeg-baseline-v1"
        },
        evidence_quality_score: {
          tier: trustTier,
          tier_score: { TIER_A: 3, TIER_B: 2, TIER_C: 1, TIER_D: 0 }[trustTier] || 0,
          timestamp: exactMatch?.trustedTimestampTokenReference ? 'trusted' : exactMatch?.registrationServerTime ? 'server' : 'none',
          algo_current: exactMatch?.fingerprintAlgorithmVersions?.fingerprint === 'phash-dct-v1',
          total_score: (function() {
            const tierScore = { TIER_A: 3, TIER_B: 2, TIER_C: 1, TIER_D: 0 }[trustTier] || 0;
            const timestampScore = exactMatch?.trustedTimestampTokenReference ? 2 : exactMatch?.registrationServerTime ? 1 : 0;
            const algoScore = exactMatch?.fingerprintAlgorithmVersions?.fingerprint === 'phash-dct-v1' ? 1 : 0;
            return tierScore + timestampScore + algoScore;
          })(),
          max_score: 6
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
        
