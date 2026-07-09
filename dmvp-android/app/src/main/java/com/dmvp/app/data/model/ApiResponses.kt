/**
 * app/src/main/java/com/dmvp/app/data/model/ApiResponses.kt
 *
 * Common API response wrappers and error structures for DMVP v3.0 Android app.
 * Provides standardized response envelopes, error handling models,
 * pagination metadata, and utility extension functions.
 *
 * These models align with the backend API specifications:
 * - Standard response envelope with metadata
 * - Error envelope with error_code, message, detail, policy_version, request_id
 * - Paginated responses with items, total, page, limit
 *
 * Uses Gson annotations for JSON serialization/deserialization.
 */

package com.dmvp.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Standard API response envelope.
 * Wraps the actual data with common metadata.
 *
 * @param T The type of the data field.
 */
data class ApiResponse<T>(
    @SerializedName("data")
    val data: T? = null,

    @SerializedName("request_id")
    val requestId: String? = null,

    @SerializedName("server_time")
    val serverTime: String? = null,

    @SerializedName("policy_version")
    val policyVersion: String? = null,

    @SerializedName("algorithm_version")
    val algorithmVersion: String? = null,

    @SerializedName("warnings")
    val warnings: List<String> = emptyList()
)

/**
 * Standard error response envelope.
 * Matches the backend error structure:
 *   {
 *     error_code: string,
 *     message: string,
 *     detail: object?,
 *     policy_version: string,
 *     request_id: string
 *   }
 */
data class ErrorResponse(
    @SerializedName("error_code")
    val errorCode: String,

    @SerializedName("message")
    val message: String,

    @SerializedName("detail")
    val detail: Map<String, Any>? = null,

    @SerializedName("policy_version")
    val policyVersion: String? = null,

    @SerializedName("request_id")
    val requestId: String? = null
)

/**
 * Paginated response wrapper.
 * Used for list endpoints that return paginated data.
 *
 * @param T The type of items in the list.
 */
data class PaginatedResponse<T>(
    @SerializedName("items")
    val items: List<T>,

    @SerializedName("total")
    val total: Int,

    @SerializedName("page")
    val page: Int,

    @SerializedName("limit")
    val limit: Int
)

/**
 * Success response for idempotent operations.
 * Used when a duplicate request returns the existing resource.
 */
data class IdempotentResponse<T>(
    @SerializedName("existing")
    val existing: Boolean,

    @SerializedName("data")
    val data: T,

    @SerializedName("request_id")
    val requestId: String? = null
)

/**
 * Verification policy metadata response.
 * Matches the GET /verify/policy endpoint.
 */
data class VerificationPolicy(
    @SerializedName("policy_version")
    val policyVersion: String,

    @SerializedName("supported_modes")
    val supportedModes: List<String>,

    @SerializedName("default_mode")
    val defaultMode: String,

    @SerializedName("similarity_thresholds")
    val similarityThresholds: SimilarityThresholds,

    @SerializedName("integrity_checks")
    val integrityChecks: List<String>,

    @SerializedName("provenance_checks")
    val provenanceChecks: List<String>,

    @SerializedName("timestamp_modes")
    val timestampModes: List<String>,

    @SerializedName("algorithm_versions")
    val algorithmVersions: Map<String, String>,

    @SerializedName("evidence_quality_mapping")
    val evidenceQualityMapping: Map<String, String>,

    @SerializedName("warnings")
    val warnings: Map<String, String>
)

/**
 * Similarity threshold configuration.
 */
data class SimilarityThresholds(
    @SerializedName("strong_derivative")
    val strongDerivative: Double,

    @SerializedName("probable_derivative")
    val probableDerivative: Double,

    @SerializedName("weak_similarity")
    val weakSimilarity: Double
)

/**
 * Utility extension functions for ApiResponse.
 */
fun <T> ApiResponse<T>.isSuccess(): Boolean = data != null

fun <T> ApiResponse<T>.getDataOrThrow(): T {
    return data ?: throw IllegalStateException("Response data is null")
}

/**
 * Utility extension for ErrorResponse.
 */
fun ErrorResponse.getUserFriendlyMessage(): String {
    // Map error codes to user-friendly messages
    return when (errorCode) {
        "VALIDATION_ERROR" -> "Please check the information you provided."
        "EVIDENCE_NOT_FOUND" -> "Evidence not found."
        "DEVICE_KEY_NOT_FOUND" -> "Device key not found."
        "DUPLICATE_EVIDENCE" -> "This evidence has already been registered."
        "DUPLICATE_DEVICE_KEY" -> "Device key already registered."
        "CLAIM_NOT_FOUND" -> "Ownership claim not found."
        "INVALID_SIGNATURE" -> "Invalid signature. Please try again."
        "REPLAY_DETECTED" -> "Request replay detected. Please try again."
        "RATE_LIMIT_EXCEEDED" -> "Too many requests. Please wait and try again."
        "INSUFFICIENT_DEVICE_TRUST" -> "Device trust level is insufficient."
        "PRIVACY_FLAG_VIOLATION" -> "Privacy settings do not allow this operation."
        "DEGRADED_TIMESTAMP_MODE" -> "Timestamp mode is degraded."
        "ALREADY_REVOKED" -> "This device key is already revoked."
        "INVALID_STATE" -> "Operation is not allowed in the current state."
        "INTERNAL_SERVER_ERROR" -> "An internal server error occurred. Please try again later."
        else -> message
    }
}

/**
 * Common error codes for reference.
 */
object ErrorCodes {
    const val VALIDATION_ERROR = "VALIDATION_ERROR"
    const val EVIDENCE_NOT_FOUND = "EVIDENCE_NOT_FOUND"
    const val DEVICE_KEY_NOT_FOUND = "DEVICE_KEY_NOT_FOUND"
    const val DUPLICATE_EVIDENCE = "DUPLICATE_EVIDENCE"
    const val DUPLICATE_DEVICE_KEY = "DUPLICATE_DEVICE_KEY"
    const val CLAIM_NOT_FOUND = "CLAIM_NOT_FOUND"
    const val INVALID_SIGNATURE = "INVALID_SIGNATURE"
    const val REPLAY_DETECTED = "REPLAY_DETECTED"
    const val RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED"
    const val INSUFFICIENT_DEVICE_TRUST = "INSUFFICIENT_DEVICE_TRUST"
    const val PRIVACY_FLAG_VIOLATION = "PRIVACY_FLAG_VIOLATION"
    const val DEGRADED_TIMESTAMP_MODE = "DEGRADED_TIMESTAMP_MODE"
    const val ALREADY_REVOKED = "ALREADY_REVOKED"
    const val INVALID_STATE = "INVALID_STATE"
    const val INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR"
}
