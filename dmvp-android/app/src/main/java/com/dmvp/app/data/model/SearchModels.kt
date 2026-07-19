/**
 * Phase 3: Search and Verification data models
 */

package com.dmvp.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Search result from cross-modal search API
 */
data class SearchResult(
    @SerializedName("evidence_id")
    val evidenceId: String,

    @SerializedName("similarity")
    val similarity: Float,

    @SerializedName("media_type")
    val mediaType: String,

    @SerializedName("sha256_original")
    val sha256Original: String,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("device_model")
    val deviceModel: String?,

    @SerializedName("trust_tier")
    val trustTier: String
)

/**
 * Search response from backend
 */
data class SearchResponse(
    @SerializedName("results")
    val matchedEvidence: List<MatchedEvidence>? = emptyList(),

    @SerializedName("total_matches")
    val totalMatches: Int = 0,

    @SerializedName("similarity_verdict")
    val bestMatchType: String? = null,

    @SerializedName("best_score")
    val bestScore: Double = 0.0,

    @SerializedName("search_metadata")
    val searchMetadata: Map<String, Any>? = null
)

/**
 * Verification verdict from 10-layer verdict engine
 */
data class VerificationVerdict(
    @SerializedName("request_id")
    val requestId: String,

    @SerializedName("timestamp")
    val timestamp: String,

    @SerializedName("verdict")
    val verdict: String, // exact_match, near_copy, derivative, similar, possible_ai_derivative, no_match

    @SerializedName("confidence")
    val confidence: Float,

    @SerializedName("layers")
    val layers: Map<String, LayerResult>,

    @SerializedName("summary")
    val summary: VerdictSummary
)

/**
 * Individual layer result
 */
data class LayerResult(
    @SerializedName("evaluated")
    val evaluated: Boolean,

    @SerializedName("signal_detected")
    val signalDetected: Boolean,

    @SerializedName("confidence")
    val confidence: Float,

    @SerializedName("details")
    val details: Map<String, Any>?
)

/**
 * Verdict summary
 */
data class VerdictSummary(
    @SerializedName("total_layers_evaluated")
    val totalLayersEvaluated: Int,

    @SerializedName("layers_with_signal")
    val layersWithSignal: Int,

    @SerializedName("highest_confidence_layer")
    val highestConfidenceLayer: String?
)

/**
 * L8 AI derivative detection result
 */
data class L8DetectionResult(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("verdict")
    val verdict: L8Verdict?,

    @SerializedName("error")
    val error: String?
)

/**
 * L8 verdict with mandatory disclosure
 */
data class L8Verdict(
    @SerializedName("status")
    val status: String, // possible_ai_derivative, unlikely_ai_derivative, no_signal

    @SerializedName("score")
    val score: Float,

    @SerializedName("fpr")
    val fpr: Float, // False Positive Rate

    @SerializedName("fnr")
    val fnr: Float, // False Negative Rate

    @SerializedName("model_versions")
    val modelVersions: Map<String, String>,

    @SerializedName("is_evidentiary_signal")
    val isEvidentiarySignal: Boolean,

    @SerializedName("disclaimer")
    val disclaimer: String,

    @SerializedName("tier1_results")
    val tier1Results: Map<String, Any>?,

    @SerializedName("tier2_results")
    val tier2Results: Map<String, Any>?
)
