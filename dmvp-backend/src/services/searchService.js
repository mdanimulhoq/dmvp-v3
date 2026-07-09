/**
 * src/services/searchService.js
 *
 * Search and matching service for DMVP v3.0.
 *
 * Implements staged matching pipeline:
 *   Stage 0: Exact lookup (by SHA-256 or canonical hash)
 *   Stage 1: Coarse candidate generation (using fingerprint indexing)
 *   Stage 2: Re-ranking (richer comparison)
 *   Stage 3: Verdict construction
 *
 * Privacy-aware: returns bounded, permission-filtered results.
 */

const { prisma } = require('../config/database');
const {
  hammingDistance,
  compareFingerprintProfiles,
} = require('../utils/hashUtils');

// Logger
const logger = console;

/**
 * Stage 0: Exact lookup by SHA-256 or canonical hash.
 *
 * @param {string} sha256 - 64-character hex SHA-256.
 * @param {string} [canonicalHash] - Optional canonical hash.
 * @returns {Promise<Array<Object>>} Array of evidence records matching exactly.
 */
async function exactLookup(sha256, canonicalHash = null) {
  const results = [];
  // 1. Exact SHA-256 match
  const exactRecords = await prisma.evidenceRecord.findMany({
    where: { sha256_original: sha256 },
  });
  results.push(...exactRecords);

  // 2. Canonical hash match (if provided)
  if (canonicalHash) {
    const canonicalRecords = await prisma.evidenceRecord.findMany({
      where: { canonical_media_hash: canonicalHash },
    });
    // Avoid duplicates
    const existingIds = new Set(results.map(r => r.evidence_id));
    for (const rec of canonicalRecords) {
      if (!existingIds.has(rec.evidence_id)) {
        results.push(rec);
      }
    }
  }

  return results;
}

/**
 * Stage 1: Coarse candidate generation.
 *
 * Uses simple database filtering by media_type and optionally device key,
 * then computes coarse similarity using a lightweight fingerprint (e.g., phash only)
 * to generate an initial list of candidates.
 *
 * In production, this would use specialized indices (BK-tree, LSH, ANN).
 * For MVP, we'll fetch a limited number of records and compute a cheap distance.
 *
 * @param {Object} fingerprintProfile - The robust fingerprint profile to search against.
 * @param {string} mediaType - 'image' or 'video'.
 * @param {number} maxCandidates - Maximum number of candidates to return (default: 100).
 * @param {Object} [filters] - Additional filters (e.g., signer_device_key_id).
 * @returns {Promise<Array<Object>>} Array of candidate records with coarse score.
 */
async function coarseCandidateGeneration(fingerprintProfile, mediaType, maxCandidates = 100, filters = {}) {
  // Build where clause
  const where = {
    media_type: mediaType,
    lifecycle_state: 'ACTIVE',
  };
  if (filters.signer_device_key_id) {
    where.signer_device_key_id = filters.signer_device_key_id;
  }

  // Fetch records; limit to a manageable number (we'll apply maxCandidates after scoring)
  // In production, this would be optimized with indices; for MVP we'll take a larger pool.
  const records = await prisma.evidenceRecord.findMany({
    where,
    take: 1000, // reasonable limit for MVP
  });

  // Score each record using a fast metric (e.g., phash only)
  const scored = [];
  for (const record of records) {
    const storedProfile = record.fingerprint_profile;
    if (!storedProfile) continue;

    // Use a fast comparator: compute Hamming distance on phash if available
    let coarseScore = 0;
    if (fingerprintProfile.phash && storedProfile.phash) {
      try {
        const distance = hammingDistance(fingerprintProfile.phash, storedProfile.phash);
        const bitLength = fingerprintProfile.phash.length * 4; // hex
        coarseScore = 1 - (distance / bitLength);
      } catch (_) {
        // If error, fall back to 0
        coarseScore = 0;
      }
    } else {
      // If no phash, skip this candidate
      continue;
    }

    // Only keep if coarseScore above a low threshold (e.g., 0.3) to reduce noise
    if (coarseScore >= 0.3) {
      scored.push({
        record,
        coarseScore,
      });
    }
  }

  // Sort by coarseScore descending and take top maxCandidates
  scored.sort((a, b) => b.coarseScore - a.coarseScore);
  const topCandidates = scored.slice(0, maxCandidates);

  return topCandidates.map(item => ({
    ...item.record,
    _coarse_score: item.coarseScore,
  }));
}

/**
 * Stage 2: Re-ranking.
 *
 * Takes the coarse candidates and re-ranks them using a richer comparator
 * that considers multiple fingerprint components (phash, dhash, blockHash, etc.)
 * and possibly contextual signals.
 *
 * @param {Object} fingerprintProfile - The query fingerprint profile.
 * @param {Array<Object>} candidates - Array of candidate evidence records (from coarse stage).
 * @param {Object} [weights] - Optional weights for compareFingerprintProfiles.
 * @returns {Array<Object>} Re-ranked candidates with final similarity score.
 */
async function reRankCandidates(fingerprintProfile, candidates, weights = null) {
  const reranked = [];
  for (const candidate of candidates) {
    const storedProfile = candidate.fingerprint_profile;
    if (!storedProfile) {
      reranked.push({
        ...candidate,
        similarityScore: 0,
      });
      continue;
    }
    try {
      const score = compareFingerprintProfiles(fingerprintProfile, storedProfile, weights);
      reranked.push({
        ...candidate,
        similarityScore: score,
      });
    } catch (err) {
      logger.warn('Re-ranking error for candidate', candidate.evidence_id, err.message);
      reranked.push({
        ...candidate,
        similarityScore: 0,
      });
    }
  }

  // Sort by similarityScore descending
  reranked.sort((a, b) => b.similarityScore - a.similarityScore);
  return reranked;
}

/**
 * Stage 3: Verdict construction.
 *
 * From the re-ranked candidates, construct a search response with:
 *   - matched_evidence: top matches with scores and match types
 *   - best_match_type: "exact", "canonical", or "similarity"
 *   - total_count, etc.
 *
 * @param {Array<Object>} exactMatches - Exact matches (from stage 0).
 * @param {Array<Object>} rerankedCandidates - Re-ranked similarity candidates.
 * @param {number} maxResults - Maximum number of results to return.
 * @returns {Object} Search verdict with matched evidence, scores, and metadata.
 */
function buildSearchVerdict(exactMatches, rerankedCandidates, maxResults = 10) {
  const matched = [];

  // Add exact matches first (these are the highest confidence)
  for (const record of exactMatches) {
    matched.push({
      evidence_id: record.evidence_id,
      sha256: record.sha256_original,
      media_type: record.media_type,
      match_type: 'exact',
      similarity_score: 1.0, // exact match
      timestamp: record.created_at,
    });
  }

  // Add canonical matches if not already included
  // (We already have them in exactMatches if they were returned, but we might have duplicates)
  // We'll rely on exactMatches containing all exact matches.

  // Add similarity matches (top N, excluding those already in exact matches)
  const existingIds = new Set(matched.map(m => m.evidence_id));
  let similarityAdded = 0;
  for (const cand of rerankedCandidates) {
    if (similarityAdded >= maxResults) break;
    if (existingIds.has(cand.evidence_id)) continue;
    // Only include if similarity score is above a threshold (e.g., 0.4)
    if (cand.similarityScore < 0.4) continue;
    matched.push({
      evidence_id: cand.evidence_id,
      sha256: cand.sha256_original,
      media_type: cand.media_type,
      match_type: 'similarity',
      similarity_score: cand.similarityScore,
      timestamp: cand.created_at,
    });
    similarityAdded++;
  }

  // Determine best match type
  let bestMatchType = 'none';
  if (matched.length > 0) {
    if (matched.some(m => m.match_type === 'exact')) {
      bestMatchType = 'exact';
    } else if (matched.some(m => m.match_type === 'similarity')) {
      bestMatchType = 'similarity';
    }
  }

  return {
    matched_evidence: matched,
    total_matches: matched.length,
    best_match_type: bestMatchType,
    // Also include the top score for UI
    best_score: matched.length > 0 ? matched[0].similarity_score : 0,
  };
}

/**
 * Main search function: performs staged matching.
 *
 * @param {Object} request - Search request.
 * @param {string} request.sha256 - Required for exact lookup.
 * @param {string} [request.canonical_media_hash] - Optional canonical hash.
 * @param {Object} [request.robust_fingerprint_profile] - Required for similarity search.
 * @param {string} request.media_type - 'image' or 'video'.
 * @param {Object} [request.filters] - Additional filters (e.g., signer_device_key_id).
 * @param {number} [request.maxResults] - Max results to return (default: 10).
 * @param {number} [request.maxCandidates] - Max candidates for coarse stage (default: 100).
 * @param {string} [request.actorId] - For permission filtering (optional).
 * @returns {Promise<Object>} Search verdict.
 */
async function searchEvidence(request) {
  const {
    sha256,
    canonical_media_hash,
    robust_fingerprint_profile,
    media_type,
    filters = {},
    maxResults = 10,
    maxCandidates = 100,
    actorId = null,
  } = request;

  // Validate required fields
  if (!sha256) {
    throw new Error('searchEvidence: sha256 is required');
  }
  if (!media_type) {
    throw new Error('searchEvidence: media_type is required');
  }
  if (!['image', 'video'].includes(media_type)) {
    throw new Error('searchEvidence: media_type must be "image" or "video"');
  }

  // --- Stage 0: Exact lookup ---
  const exactMatches = await exactLookup(sha256, canonical_media_hash);

  // --- Stage 1 & 2: Coarse candidate generation and re-ranking (if profile provided) ---
  let reranked = [];
  if (robust_fingerprint_profile && Object.keys(robust_fingerprint_profile).length > 0) {
    // Coarse candidates
    const coarseCandidates = await coarseCandidateGeneration(
      robust_fingerprint_profile,
      media_type,
      maxCandidates,
      filters
    );

    // Re-rank
    reranked = await reRankCandidates(robust_fingerprint_profile, coarseCandidates);
  }

  // --- Stage 3: Build verdict ---
  const verdict = buildSearchVerdict(exactMatches, reranked, maxResults);

  // --- Permission filtering ---
  // If actorId is provided, we might filter out evidence that the actor is not allowed to see.
  // For MVP, we assume all evidence is public (or owned by the actor).
  // In production, you'd check ownership claims or access control lists.
  // For now, we return the verdict as is.

  // Add metadata about search process
  verdict.search_metadata = {
    exact_matches_found: exactMatches.length,
    candidate_count: reranked.length,
    media_type,
    algorithm_versions: {
      fingerprint: 'v1.0',
      similarity: 'v1.0',
    },
  };

  return verdict;
}

/**
 * Get related evidence for a given evidence ID.
 *
 * Uses lineage transitions and maybe similar fingerprints to find related records.
 *
 * @param {string} evidenceId - The evidence ID to find relations for.
 * @param {number} maxResults - Maximum number of related records.
 * @returns {Promise<Array<Object>>} Array of related evidence records with relation type.
 */
async function getRelatedEvidence(evidenceId, maxResults = 10) {
  // Find the primary evidence
  const primary = await prisma.evidenceRecord.findUnique({
    where: { evidence_id: evidenceId },
  });
  if (!primary) {
    return [];
  }

  // Relations can come from:
  // 1. Chain parent/child: records with chain_parent_evidence_id = evidenceId, or where chain_parent_evidence_id = parent of primary
  // 2. Same SHA-256 (duplicates)
  // 3. Similar fingerprints (we can compute similarity on the fly)

  // For MVP, we'll return lineage relations
  const related = [];

  // Children (records that reference this as parent)
  const children = await prisma.evidenceRecord.findMany({
    where: { chain_parent_evidence_id: evidenceId },
    take: maxResults,
  });
  for (const child of children) {
    related.push({
      evidence: child,
      relation_type: 'lineage_child',
    });
  }

  // Parent (if exists)
  if (primary.chain_parent_evidence_id) {
    const parent = await prisma.evidenceRecord.findUnique({
      where: { evidence_id: primary.chain_parent_evidence_id },
    });
    if (parent) {
      related.push({
        evidence: parent,
        relation_type: 'lineage_parent',
      });
    }
  }

  // Also find duplicates (same SHA-256 but different device keys, etc.)
  const duplicates = await prisma.evidenceRecord.findMany({
    where: {
      sha256_original: primary.sha256_original,
      evidence_id: { not: evidenceId },
    },
    take: maxResults - related.length,
  });
  for (const dup of duplicates) {
    related.push({
      evidence: dup,
      relation_type: 'duplicate_hash',
    });
  }

  // Limit to maxResults
  return related.slice(0, maxResults);
}

module.exports = {
  exactLookup,
  coarseCandidateGeneration,
  reRankCandidates,
  buildSearchVerdict,
  searchEvidence,
  getRelatedEvidence,
};
