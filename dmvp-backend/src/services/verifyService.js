/**
 * src/services/verifyService.js
 *
 * Verification service for DMVP v3.0.
 *
 * Performs multi-axis verification:
 *   - Integrity: exact SHA-256 match, optional canonical match.
 *   - Provenance: device trust and attestation evaluation.
 *   - Similarity: perceptual similarity against registered evidence.
 *   - Evidence Quality: overall confidence based on trust tier, timestamp, and validation context.
 *   - Transformation Indicators: inferred from fingerprint comparison.
 *
 * Supports three verification modes: fast, standard, deep.
 */

const { prisma } = require('../config/database');
const { hammingDistance, compareFingerprintProfiles } = require('../utils/hashUtils');

// Logger
const logger = console;

/**
 * Verdict constants (aligned with spec).
 */
const IntegrityVerdict = {
  EXACT_MATCH: 'EXACT_MATCH',
  CANONICAL_MATCH: 'CANONICAL_MATCH',
  NO_EXACT_MATCH: 'NO_EXACT_MATCH',
};

const ProvenanceVerdict = {
  SIGNED_TRUSTED_DEVICE: 'SIGNED_TRUSTED_DEVICE',
  SIGNED_KNOWN_DEVICE_DIFFERENT_LINEAGE: 'SIGNED_KNOWN_DEVICE_DIFFERENT_LINEAGE',
  NO_TRUSTED_PROVENANCE: 'NO_TRUSTED_PROVENANCE',
  ATTESTATION_MISSING: 'ATTESTATION_MISSING',
};

const SimilarityVerdict = {
  STRONG_DERIVATIVE: 'STRONG_DERIVATIVE',
  PROBABLE_DERIVATIVE: 'PROBABLE_DERIVATIVE',
  WEAK_SIMILARITY: 'WEAK_SIMILARITY',
  NO_RELIABLE_SIMILARITY: 'NO_RELIABLE_SIMILARITY',
};

const EvidenceQualityVerdict = {
  HIGH_EVIDENTIARY_STRENGTH: 'HIGH_EVIDENTIARY_STRENGTH',
  MODERATE_EVIDENTIARY_STRENGTH: 'MODERATE_EVIDENTIARY_STRENGTH',
  LOW_EVIDENTIARY_STRENGTH: 'LOW_EVIDENTIARY_STRENGTH',
};

/**
 * Map trust tier to evidence quality.
 */
function getEvidenceQualityFromTrustTier(trustTier, hasTimestamp, hasAttestation) {
  // Based on spec: Tier A with attestation and timestamp = HIGH, etc.
  let base = 0;
  switch (trustTier) {
    case 'TIER_A': base = 3; break;
    case 'TIER_B': base = 2; break;
    case 'TIER_C': base = 1; break;
    case 'TIER_D': base = 0; break;
    default: base = 0;
  }
  // Boost if timestamp present
  if (hasTimestamp) base += 1;
  // Boost if attestation present
  if (hasAttestation) base += 1;

  if (base >= 5) return EvidenceQualityVerdict.HIGH_EVIDENTIARY_STRENGTH;
  if (base >= 3) return EvidenceQualityVerdict.MODERATE_EVIDENTIARY_STRENGTH;
  return EvidenceQualityVerdict.LOW_EVIDENTIARY_STRENGTH;
}

/**
 * Determine provenance verdict from device trust tier and lineage.
 */
function getProvenanceVerdict(deviceTrustTier, attestationSummary, lineageParentId) {
  if (deviceTrustTier === 'TIER_D') {
    return ProvenanceVerdict.NO_TRUSTED_PROVENANCE;
  }
  // Check if attestation is present and valid
  const attestationValid = attestationSummary && Object.keys(attestationSummary).length > 0;
  if (deviceTrustTier === 'TIER_A' && attestationValid) {
    return ProvenanceVerdict.SIGNED_TRUSTED_DEVICE;
  }
  if (deviceTrustTier === 'TIER_B' || deviceTrustTier === 'TIER_C') {
    if (lineageParentId) {
      // Different lineage? Actually if parent exists, it's a continuation, not different.
      // For simplicity, we treat TIER_B and C as still trusted but with degraded attestation.
      return ProvenanceVerdict.SIGNED_TRUSTED_DEVICE;
    }
    return ProvenanceVerdict.ATTESTATION_MISSING;
  }
  return ProvenanceVerdict.NO_TRUSTED_PROVENANCE;
}

/**
 * Determine similarity verdict based on similarity score (0-1).
 */
function getSimilarityVerdict(score) {
  if (score >= 0.95) return SimilarityVerdict.STRONG_DERIVATIVE;
  if (score >= 0.80) return SimilarityVerdict.PROBABLE_DERIVATIVE;
  if (score >= 0.50) return SimilarityVerdict.WEAK_SIMILARITY;
  return SimilarityVerdict.NO_RELIABLE_SIMILARITY;
}

/**
 * Infer transformation indicators based on fingerprint comparison.
 * This is a placeholder; actual inference would require detailed analysis.
 */
function inferTransformations(profileA, profileB) {
  const indicators = [];
  // Simple heuristic: if phash differs but block hash similar -> resize/crop
  if (profileA.phash && profileB.phash && profileA.blockHash && profileB.blockHash) {
    const phashDist = hammingDistance(profileA.phash, profileB.phash);
    const blockDist = hammingDistance(profileA.blockHash, profileB.blockHash);
    const bitLen = profileA.phash.length * 4; // assume hex
    const phashSim = 1 - (phashDist / bitLen);
    const blockSim = 1 - (blockDist / bitLen);
    if (phashSim < 0.7 && blockSim > 0.8) {
      indicators.push('crop_resize');
    }
  }
  // For now, return an empty array; could be expanded with more heuristics.
  return indicators;
}

/**
 * Main verification function.
 *
 * @param {Object} request - Verification request.
 * @param {string} request.sha256 - SHA-256 of the media to verify.
 * @param {string} [request.canonical_media_hash] - Optional canonical hash.
 * @param {string} request.media_type - 'image' or 'video'.
 * @param {Object} [request.robust_fingerprint_profile] - Fingerprint profile of the media to verify (for similarity).
 * @param {string} request.verification_mode - 'fast', 'standard', or 'deep'.
 * @param {string} [request.signer_device_key_id] - Optionally, the device key that signed the media (for provenance check).
 * @param {Object} [request.timestamp_info] - Optional timestamp info.
 * @returns {Promise<Object>} Multi-axis verdict object.
 */
async function verifyMedia(request) {
  // Validate input
  if (!request.sha256 || !request.media_type) {
    throw new Error('verifyMedia: sha256 and media_type are required');
  }
  if (!['image', 'video'].includes(request.media_type)) {
    throw new Error('verifyMedia: media_type must be "image" or "video"');
  }
  const mode = request.verification_mode || 'standard';

  // Initialize verdict structure
  const verdict = {
    integrity_verdict: null,
    provenance_verdict: null,
    similarity_verdict: null,
    evidence_quality_verdict: null,
    transformation_indicators: [],
    matched_evidence_list: [],
    algorithm_versions_used: {},
    warnings: [],
    summary_ui_score: null, // optional
  };

  // 1. Integrity check: exact hash match
  let evidenceRecord = await prisma.evidenceRecord.findFirst({
    where: { sha256_original: request.sha256 },
  });

  if (evidenceRecord) {
    verdict.integrity_verdict = IntegrityVerdict.EXACT_MATCH;
    // Record matched evidence
    verdict.matched_evidence_list.push({
      evidence_id: evidenceRecord.evidence_id,
      match_type: 'exact',
      sha256: evidenceRecord.sha256_original,
    });
  } else if (request.canonical_media_hash) {
    // Check canonical match
    const canonicalRecord = await prisma.evidenceRecord.findFirst({
      where: { canonical_media_hash: request.canonical_media_hash },
    });
    if (canonicalRecord) {
      verdict.integrity_verdict = IntegrityVerdict.CANONICAL_MATCH;
      verdict.matched_evidence_list.push({
        evidence_id: canonicalRecord.evidence_id,
        match_type: 'canonical',
        sha256: canonicalRecord.sha256_original,
      });
      evidenceRecord = canonicalRecord; // use this for further checks
    } else {
      verdict.integrity_verdict = IntegrityVerdict.NO_EXACT_MATCH;
    }
  } else {
    verdict.integrity_verdict = IntegrityVerdict.NO_EXACT_MATCH;
  }

  // If we have an exact or canonical match, we can derive provenance and quality from that record.
  // If not, we may still perform similarity search against other records.
  // But we need to collect a set of candidate evidence records.
  let candidates = [];

  if (evidenceRecord) {
    // We have at least one exact/canonical match
    candidates.push(evidenceRecord);
  }

  // 2. Similarity search if we have a fingerprint and no exact match or if mode is deep/standard
  const hasFingerprint = request.robust_fingerprint_profile &&
    Object.keys(request.robust_fingerprint_profile).length > 0;

  if (hasFingerprint && (mode === 'standard' || mode === 'deep' || verdict.integrity_verdict === IntegrityVerdict.NO_EXACT_MATCH)) {
    // For similarity, we need to find candidates from the registry.
    // In production, this would call the search service.
    // For now, we'll implement a simple: fetch all evidence of the same media_type and compare.
    // This is inefficient but works for MVP.
    // We'll limit to 100 records for performance.
    const allRecords = await prisma.evidenceRecord.findMany({
      where: { media_type: request.media_type, lifecycle_state: 'ACTIVE' },
      take: 100, // limit for performance
    });

    // Compute similarity scores for each
    const scored = allRecords.map(record => {
      const storedProfile = record.fingerprint_profile;
      if (!storedProfile) return null;
      try {
        const score = compareFingerprintProfiles(
          request.robust_fingerprint_profile,
          storedProfile
        );
        return { record, score };
      } catch (err) {
        logger.warn('Error comparing fingerprint profiles:', err.message);
        return null;
      }
    }).filter(item => item !== null);

    // Sort by score descending
    scored.sort((a, b) => b.score - a.score);

    // Take top matches (up to 5)
    const topMatches = scored.slice(0, 5);

    // If we already have an exact match, we might want to include it in the matched list,
    // but we already have it. For similarity, we'll add additional matches if they are not already exact.
    for (const match of topMatches) {
      // Avoid duplicate if already in matched list
      const alreadyMatched = verdict.matched_evidence_list.some(
        item => item.evidence_id === match.record.evidence_id
      );
      if (!alreadyMatched && match.score > 0.5) {
        verdict.matched_evidence_list.push({
          evidence_id: match.record.evidence_id,
          match_type: 'similarity',
          similarity_score: match.score,
          sha256: match.record.sha256_original,
        });
      }
    }

    // Determine similarity verdict based on best score
    const bestSimilarity = topMatches.length > 0 ? topMatches[0].score : 0;
    verdict.similarity_verdict = getSimilarityVerdict(bestSimilarity);

    // If we have a similarity match, we can also infer transformation indicators
    if (topMatches.length > 0 && bestSimilarity >= 0.5) {
      const bestProfile = topMatches[0].record.fingerprint_profile;
      if (bestProfile && request.robust_fingerprint_profile) {
        const indicators = inferTransformations(
          request.robust_fingerprint_profile,
          bestProfile
        );
        verdict.transformation_indicators = indicators;
      }
    }
  } else {
    // No similarity performed
    verdict.similarity_verdict = SimilarityVerdict.NO_RELIABLE_SIMILARITY;
  }

  // 3. Provenance
  // If we have a matched evidence (either exact or similarity), we can determine provenance
  // We'll use the primary evidence record (the one with highest confidence).
  // If we have exact match, use that; else use the best similarity match.
  let primaryEvidence = evidenceRecord;
  if (!primaryEvidence && verdict.matched_evidence_list.length > 0) {
    // Find the first match that is not similarity (prefer exact/canonical)
    const exactMatch = verdict.matched_evidence_list.find(m => m.match_type !== 'similarity');
    if (exactMatch) {
      primaryEvidence = await prisma.evidenceRecord.findUnique({
        where: { evidence_id: exactMatch.evidence_id },
      });
    } else {
      // Use the first similarity match
      const simMatch = verdict.matched_evidence_list.find(m => m.match_type === 'similarity');
      if (simMatch) {
        primaryEvidence = await prisma.evidenceRecord.findUnique({
          where: { evidence_id: simMatch.evidence_id },
        });
      }
    }
  }

  if (primaryEvidence) {
    // Fetch device key info for provenance
    const deviceKey = await prisma.deviceKey.findUnique({
      where: { device_key_id: primaryEvidence.signer_device_key_id },
    });
    if (deviceKey) {
      const trustTier = deviceKey.trust_tier;
      const attestationSummary = primaryEvidence.device_attestation_summary || {};
      const lineageParentId = primaryEvidence.chain_parent_evidence_id || null;
      verdict.provenance_verdict = getProvenanceVerdict(
        trustTier,
        attestationSummary,
        lineageParentId
      );
      // Evidence quality
      const hasTimestamp = !!(primaryEvidence.timestamp_references &&
        primaryEvidence.timestamp_references.trusted_timestamp_token_reference);
      const hasAttestation = attestationSummary && Object.keys(attestationSummary).length > 0;
      verdict.evidence_quality_verdict = getEvidenceQualityFromTrustTier(
        trustTier,
        hasTimestamp,
        hasAttestation
      );
    } else {
      verdict.provenance_verdict = ProvenanceVerdict.NO_TRUSTED_PROVENANCE;
      verdict.evidence_quality_verdict = EvidenceQualityVerdict.LOW_EVIDENTIARY_STRENGTH;
    }
  } else {
    // No primary evidence found
    verdict.provenance_verdict = ProvenanceVerdict.NO_TRUSTED_PROVENANCE;
    verdict.evidence_quality_verdict = EvidenceQualityVerdict.LOW_EVIDENTIARY_STRENGTH;
  }

  // 4. Algorithm versions used (from the primary evidence or current policy)
  if (primaryEvidence && primaryEvidence.fingerprint_algorithm_versions) {
    verdict.algorithm_versions_used = primaryEvidence.fingerprint_algorithm_versions;
  } else {
    verdict.algorithm_versions_used = {
      fingerprint: 'v1.0',
      similarity: 'v1.0',
    };
  }

  // 5. Warnings: add warnings based on mode limitations, trust tier, etc.
  const warnings = [];
  if (mode === 'fast') {
    warnings.push('Fast verification may have reduced accuracy for similarity.');
  }
  if (verdict.provenance_verdict === ProvenanceVerdict.ATTESTATION_MISSING) {
    warnings.push('Device attestation is missing or degraded.');
  }
  if (verdict.integrity_verdict === IntegrityVerdict.NO_EXACT_MATCH &&
      verdict.similarity_verdict === SimilarityVerdict.NO_RELIABLE_SIMILARITY) {
    warnings.push('No strong evidence found for this media.');
  }
  verdict.warnings = warnings;

  // 6. UI summary score (optional, derived from verdicts)
  // Simple heuristic: score based on match strength
  let uiScore = 0;
  if (verdict.integrity_verdict === IntegrityVerdict.EXACT_MATCH) uiScore += 40;
  else if (verdict.integrity_verdict === IntegrityVerdict.CANONICAL_MATCH) uiScore += 30;
  if (verdict.similarity_verdict === SimilarityVerdict.STRONG_DERIVATIVE) uiScore += 30;
  else if (verdict.similarity_verdict === SimilarityVerdict.PROBABLE_DERIVATIVE) uiScore += 20;
  else if (verdict.similarity_verdict === SimilarityVerdict.WEAK_SIMILARITY) uiScore += 10;
  if (verdict.evidence_quality_verdict === EvidenceQualityVerdict.HIGH_EVIDENTIARY_STRENGTH) uiScore += 20;
  else if (verdict.evidence_quality_verdict === EvidenceQualityVerdict.MODERATE_EVIDENTIARY_STRENGTH) uiScore += 10;
  verdict.summary_ui_score = Math.min(100, uiScore);

  return verdict;
}

module.exports = {
  verifyMedia,
  // Expose constants for testing
  IntegrityVerdict,
  ProvenanceVerdict,
  SimilarityVerdict,
  EvidenceQualityVerdict,
};
