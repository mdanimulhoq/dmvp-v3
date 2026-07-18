/**
 * app/src/main/java/com/dmvp/app/data/model/MultiAxisVerdict.kt
 *
 * Multi-axis verdict data model for DMVP v3.0.
 * Replaces the single trust score with separate dimensions:
 *   - Integrity
 *   - Provenance
 *   - Similarity
 *   - Evidence Quality
 *   - Transformation Indicators
 *   - Trust Tier
 *
 * Each verdict includes the verdict value, optional score, and supporting metadata.
 * Uses Gson annotations for JSON serialization/deserialization.
 */

package com.dmvp.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Complete multi-axis verification verdict returned from the /verify endpoint.
 */
data class MultiAxisVerdict(
    // Integrity verdict
    @SerializedName("integrity_verdict")
    val integrityVerdict: IntegrityVerdict? = null,

    // Provenance verdict
    @SerializedName("provenance_verdict")
    val provenanceVerdict: ProvenanceVerdict? = null,

    // Similarity verdict
    @SerializedName("similarity_verdict")
    val similarityVerdict: SimilarityVerdict? = null,

    // Evidence quality verdict
    @SerializedName("evidence_quality_verdict")
    val evidenceQualityVerdict: EvidenceQualityVerdict? = null,

    // Transformation indicators (list of inferred transformations)
    @SerializedName("transformation_indicators")
    val transformationIndicators: List<TransformationIndicator> = emptyList(),

    // Matched evidence list (references to registered evidence)
    @SerializedName("matched_evidence_list")
    val matchedEvidenceList: List<MatchedEvidence> = emptyList(),

    // Algorithm versions used
    @SerializedName("algorithm_versions_used")
    val algorithmVersionsUsed: Map<String, String> = emptyMap(),

    // ── Step 7.2: Policy version used for verification ──
    @SerializedName("policy_version")
    val policyVersion: String? = null,

    // Warnings (list of warning messages)
    @SerializedName("warnings")
    val warnings: List<String> = emptyList(),

    // Optional UI summary score (0-100, derived from verdicts for convenience)
    @SerializedName("summary_ui_score")
    val summaryUiScore: Int? = null,

    // Additional metadata (e.g., timestamp, request ID)
    @SerializedName("metadata")
    val metadata: Map<String, Any>? = null
)

/**
 * Integrity verdict values.
 */
enum class IntegrityVerdict {
    @SerializedName("EXACT_MATCH")
    EXACT_MATCH,

    @SerializedName("CANONICAL_MATCH")
    CANONICAL_MATCH,

    @SerializedName("NO_EXACT_MATCH")
    NO_EXACT_MATCH
}

/**
 * Provenance verdict values.
 */
enum class ProvenanceVerdict {
    @SerializedName("SIGNED_TRUSTED_DEVICE")
    SIGNED_TRUSTED_DEVICE,

    @SerializedName("SIGNED_KNOWN_DEVICE_DIFFERENT_LINEAGE")
    SIGNED_KNOWN_DEVICE_DIFFERENT_LINEAGE,

    @SerializedName("NO_TRUSTED_PROVENANCE")
    NO_TRUSTED_PROVENANCE,

    @SerializedName("ATTESTATION_MISSING")
    ATTESTATION_MISSING
}

/**
 * Similarity verdict values.
 */
enum class SimilarityVerdict {
    @SerializedName("STRONG_DERIVATIVE")
    STRONG_DERIVATIVE,

    @SerializedName("PROBABLE_DERIVATIVE")
    PROBABLE_DERIVATIVE,

    @SerializedName("WEAK_SIMILARITY")
    WEAK_SIMILARITY,

    @SerializedName("NO_RELIABLE_SIMILARITY")
    NO_RELIABLE_SIMILARITY
}

/**
 * Evidence quality verdict values.
 */
enum class EvidenceQualityVerdict {
    @SerializedName("HIGH_EVIDENTIARY_STRENGTH")
    HIGH_EVIDENTIARY_STRENGTH,

    @SerializedName("MODERATE_EVIDENTIARY_STRENGTH")
    MODERATE_EVIDENTIARY_STRENGTH,

    @SerializedName("LOW_EVIDENTIARY_STRENGTH")
    LOW_EVIDENTIARY_STRENGTH
}

/**
 * Transformation indicators inferred from comparison.
 */
enum class TransformationIndicator {
    @SerializedName("compression_detected")
    COMPRESSION_DETECTED,

    @SerializedName("transcode_likely")
    TRANSCODE_LIKELY,

    @SerializedName("trim_likely")
    TRIM_LIKELY,

    @SerializedName("subtitle_overlay")
    SUBTITLE_OVERLAY,

    @SerializedName("crop_resize")
    CROP_RESIZE,

    @SerializedName("frame_rate_change")
    FRAME_RATE_CHANGE
}

/**
 * Matched evidence reference.
 */
data class MatchedEvidence(
    @SerializedName("evidence_id")
    val evidenceId: String? = null,

    @SerializedName("sha256")
    val sha256: String? = null,

    @SerializedName("match_type")
    val matchType: String? = null, // "exact", "canonical", or "similarity"

    @SerializedName("similarity_score")
    val similarityScore: Double? = null,

    @SerializedName("timestamp")
    val timestamp: String? = null
)

/**
 * Convenience extension functions for verdict interpretation.
 */
fun MultiAxisVerdict.isIntegrityVerified(): Boolean {
    return integrityVerdict == IntegrityVerdict.EXACT_MATCH ||
            integrityVerdict == IntegrityVerdict.CANONICAL_MATCH
}

fun MultiAxisVerdict.isProvenanceVerified(): Boolean {
    return provenanceVerdict == ProvenanceVerdict.SIGNED_TRUSTED_DEVICE ||
            provenanceVerdict == ProvenanceVerdict.SIGNED_KNOWN_DEVICE_DIFFERENT_LINEAGE
}

fun MultiAxisVerdict.hasStrongSimilarity(): Boolean {
    return similarityVerdict == SimilarityVerdict.STRONG_DERIVATIVE ||
            similarityVerdict == SimilarityVerdict.PROBABLE_DERIVATIVE
}

fun MultiAxisVerdict.isHighQuality(): Boolean {
    return evidenceQualityVerdict == EvidenceQualityVerdict.HIGH_EVIDENTIARY_STRENGTH
}

fun MultiAxisVerdict.getUiScore(): Int {
    return summaryUiScore ?: 0
}

/**
 * Get a human-readable description of the overall verification result.
 */
fun MultiAxisVerdict.getOverallSummary(): String {
    return when {
        isIntegrityVerified() && isProvenanceVerified() && isHighQuality() -> "Verified - Strong evidence"
        isIntegrityVerified() && isProvenanceVerified() -> "Verified - Moderate evidence"
        isIntegrityVerified() -> "Integrity verified, but provenance uncertain"
        hasStrongSimilarity() && isProvenanceVerified() -> "Similar derivative found with trusted provenance"
        hasStrongSimilarity() -> "Similar derivative found, but provenance uncertain"
        else -> "No strong evidence found"
    }
}
