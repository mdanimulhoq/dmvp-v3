/**
 * app/src/main/java/com/dmvp/app/data/remote/ApiService.kt
 *
 * Retrofit API service interface for DMVP v3.0.
 * Defines all network endpoints with suspend functions for Coroutines.
 *
 * All endpoints include:
 *   - Request headers (Authorization, Idempotency-Key, X-Request-Signature, X-Nonce, X-Timestamp)
 *   - Request bodies as data classes
 *   - Response types as ApiResponse<T> or direct objects
 *
 * Error handling is performed at the interceptor/network layer.
 */

package com.dmvp.app.data.remote

import com.dmvp.app.data.model.*
import com.dmvp.app.utils.ApiConstants
import com.google.gson.annotations.SerializedName
import retrofit2.http.*

/**
 * Main API service interface for all DMVP backend operations.
 */
interface ApiService {

    // ============================
    // Evidence Endpoints
    // ============================

    /**
     * Register a new evidence record.
     * Requires signed request (signature, nonce, timestamp headers).
     */
    @POST(ApiConstants.ENDPOINT_EVIDENCE)
    suspend fun registerEvidence(
        @Header(ApiConstants.HEADER_IDEMPOTENCY_KEY) idempotencyKey: String? = null,
        @Header(ApiConstants.HEADER_REQUEST_SIGNATURE) signature: String,
        @Header(ApiConstants.HEADER_NONCE) nonce: String,
        @Header(ApiConstants.HEADER_TIMESTAMP) timestamp: String,
        @Header(ApiConstants.HEADER_POLICY_VERSION) policyVersion: String? = null,
        @Body cee: CEE
    ): ApiResponse<EvidenceRecord>

    /**
     * Get evidence by ID.
     */
    @GET("${ApiConstants.ENDPOINT_EVIDENCE}/{id}")
    suspend fun getEvidenceById(
        @Path("id") evidenceId: String,
        @Header(ApiConstants.HEADER_AUTHORIZATION) auth: String
    ): EvidenceRecord

    /**
     * Get evidence by SHA-256 hash (exact hash lookup).
     */
    @GET("${ApiConstants.ENDPOINT_EVIDENCE_BY_HASH}/{sha256}")
    suspend fun getEvidenceByHash(
        @Path("sha256") sha256: String,
        @Header(ApiConstants.HEADER_AUTHORIZATION) auth: String
    ): EvidenceRecord

    // ============================
    // Verification Endpoints
    // ============================

    /**
     * Submit a verification request.
     */
    @POST(ApiConstants.ENDPOINT_VERIFY)
    suspend fun verifyMedia(
        @Header(ApiConstants.HEADER_AUTHORIZATION) auth: String,
        @Body request: VerificationRequest
    ): MultiAxisVerdict

    /**
     * Get current verification policy metadata.
     */
    @GET(ApiConstants.ENDPOINT_VERIFY_POLICY)
    suspend fun getVerificationPolicy(
        @Header(ApiConstants.HEADER_AUTHORIZATION) auth: String
    ): VerificationPolicy

    // ============================
    // Search Endpoints
    // ============================

    /**
     * Search for evidence using staged matching.
     */
    @POST(ApiConstants.ENDPOINT_SEARCH)
    suspend fun searchEvidence(
        @Header(ApiConstants.HEADER_AUTHORIZATION) auth: String,
        @Body request: SearchRequest
    ): SearchResponse

    /**
     * Get related evidence for a given evidence ID.
     */
    @GET("${ApiConstants.ENDPOINT_SEARCH_RELATED}/{evidence_id}")
    suspend fun getRelatedEvidence(
        @Path("evidence_id") evidenceId: String,
        @Header(ApiConstants.HEADER_AUTHORIZATION) auth: String,
        @Query(ApiConstants.PARAM_MAX_RESULTS) maxResults: Int = 10
    ): RelatedEvidenceResponse

    // ============================
    // Device Lifecycle Endpoints
    // ============================

    /**
     * Register a new device key.
     */
    @POST(ApiConstants.ENDPOINT_DEVICES_REGISTER)
    suspend fun registerDevice(
        @Header(ApiConstants.HEADER_AUTHORIZATION) auth: String,
        @Body request: DeviceRegistrationRequest
    ): DeviceKey

    /**
     * Rotate to a new device key.
     */
    @POST("${ApiConstants.ENDPOINT_DEVICES_ROTATE}/{device_key_id}")
    suspend fun rotateDeviceKey(
        @Path("device_key_id") deviceKeyId: String,
        @Header(ApiConstants.HEADER_AUTHORIZATION) auth: String,
        @Body request: DeviceRotationRequest
    ): DeviceKey

    /**
     * Revoke a device key.
     */
    @POST("${ApiConstants.ENDPOINT_DEVICES_REVOKE}/{device_key_id}")
    suspend fun revokeDeviceKey(
        @Path("device_key_id") deviceKeyId: String,
        @Header(ApiConstants.HEADER_AUTHORIZATION) auth: String
    ): DeviceKey

    /**
     * Recover device lineage after loss.
     */
    @POST(ApiConstants.ENDPOINT_DEVICES_RECOVER)
    suspend fun recoverDeviceLineage(
        @Header(ApiConstants.HEADER_AUTHORIZATION) auth: String,
        @Body request: DeviceRecoveryRequest
    ): DeviceKey

    /**
     * Get device key information.
     */
    @GET("${ApiConstants.ENDPOINT_DEVICES_INFO}/{device_key_id}")
    suspend fun getDeviceKey(
        @Path("device_key_id") deviceKeyId: String,
        @Header(ApiConstants.HEADER_AUTHORIZATION) auth: String
    ): DeviceKey

    /**
     * List device keys with optional filters.
     */
    @GET(ApiConstants.ENDPOINT_DEVICES_INFO)
    suspend fun listDeviceKeys(
        @Header(ApiConstants.HEADER_AUTHORIZATION) auth: String,
        @Query(ApiConstants.PARAM_TRUST_TIER) trustTier: String? = null,
        @Query(ApiConstants.PARAM_LIFECYCLE_STATE) lifecycleState: String? = null,
        @Query(ApiConstants.PARAM_PAGE) page: Int = 1,
        @Query(ApiConstants.PARAM_LIMIT) limit: Int = 20
    ): DeviceListResponse

    // ============================
    // Ownership Claim Endpoints
    // ============================

    /**
     * Submit an ownership claim.
     */
    @POST(ApiConstants.ENDPOINT_OWNERSHIP_CLAIM)
    suspend fun submitOwnershipClaim(
        @Header(ApiConstants.HEADER_AUTHORIZATION) auth: String,
        @Body request: OwnershipClaimRequest
    ): OwnershipClaim

    /**
     * Get all claims for an evidence record.
     */
    @GET("${ApiConstants.ENDPOINT_OWNERSHIP_BY_EVIDENCE}/{evidence_id}")
    suspend fun getOwnershipClaims(
        @Path("evidence_id") evidenceId: String,
        @Header(ApiConstants.HEADER_AUTHORIZATION) auth: String
    ): OwnershipClaimListResponse

    /**
     * Get a specific claim by ID.
     */
    @GET("${ApiConstants.ENDPOINT_OWNERSHIP_BY_EVIDENCE}/{evidence_id}/claim/{claim_id}")
    suspend fun getOwnershipClaim(
        @Path("evidence_id") evidenceId: String,
        @Path("claim_id") claimId: String,
        @Header(ApiConstants.HEADER_AUTHORIZATION) auth: String
    ): OwnershipClaim

    /**
     * Review a claim (admin only).
     */
    @PUT("${ApiConstants.ENDPOINT_OWNERSHIP_BY_EVIDENCE}/{evidence_id}/claim/{claim_id}/review")
    suspend fun reviewOwnershipClaim(
        @Path("evidence_id") evidenceId: String,
        @Path("claim_id") claimId: String,
        @Header(ApiConstants.HEADER_AUTHORIZATION) auth: String,
        @Body request: OwnershipClaimReviewRequest
    ): OwnershipClaim

    // ============================
    // Premium Storage Endpoints
    // ============================

    /**
     * Upload encrypted original media to premium backup.
     */
    @POST(ApiConstants.ENDPOINT_PREMIUM_BACKUP)
    suspend fun backupMedia(
        @Header(ApiConstants.HEADER_AUTHORIZATION) auth: String,
        @Body request: BackupRequest
    ): BackupResponse

    /**
     * Restore original media from premium backup.
     */
    @POST(ApiConstants.ENDPOINT_PREMIUM_RESTORE)
    suspend fun restoreMedia(
        @Header(ApiConstants.HEADER_AUTHORIZATION) auth: String,
        @Body request: RestoreRequest
    ): RestoreResponse

    /**
     * Get premium backup status.
     */
    @GET(ApiConstants.ENDPOINT_PREMIUM_STATUS)
    suspend fun getPremiumStatus(
        @Header(ApiConstants.HEADER_AUTHORIZATION) auth: String
    ): PremiumStatus

    // ============================
    // Audit and Admin Endpoints
    // ============================

    /**
     * Export audit logs (admin only).
     */
    @GET(ApiConstants.ENDPOINT_AUDIT_EXPORT)
    suspend fun exportAuditLogs(
        @Header(ApiConstants.HEADER_AUTHORIZATION) auth: String,
        @Query("start") startTime: String? = null,
        @Query("end") endTime: String? = null,
        @Query("format") format: String = "json"
    ): AuditExportResponse

    /**
     * Get current policy version.
     */
    @GET(ApiConstants.ENDPOINT_POLICIES_VERSION)
    suspend fun getPolicyVersion(
        @Header(ApiConstants.HEADER_AUTHORIZATION) auth: String
    ): PolicyVersionResponse
}

// ================================
// Request/Response DTOs
// ================================

/**
 * Request body for verification.
 */
data class VerificationRequest(
    @SerializedName("sha256") val sha256: String,
    @SerializedName("canonical_media_hash") val canonicalMediaHash: String? = null,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("robust_fingerprint_profile") val robustFingerprintProfile: RobustFingerprint? = null,
    @SerializedName("verification_mode") val verificationMode: String = "standard",
    @SerializedName("signer_device_key_id") val signerDeviceKeyId: String? = null,
    @SerializedName("timestamp_info") val timestampInfo: Map<String, Any>? = null
)

/**
 * Request body for search.
 */
data class SearchRequest(
    @SerializedName("sha256") val sha256: String,
    @SerializedName("canonical_media_hash") val canonicalMediaHash: String? = null,
    @SerializedName("robust_fingerprint_profile") val robustFingerprintProfile: RobustFingerprint? = null,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("filters") val filters: Map<String, String>? = null,
    @SerializedName("maxResults") val maxResults: Int = 10,
    @SerializedName("maxCandidates") val maxCandidates: Int = 100
)

/**
 * Response for search.
 */
data class SearchResponse(
    @SerializedName("matched_evidence") val matchedEvidence: List<MatchedEvidence>,
    @SerializedName("total_matches") val totalMatches: Int,
    @SerializedName("best_match_type") val bestMatchType: String,
    @SerializedName("best_score") val bestScore: Double,
    @SerializedName("search_metadata") val searchMetadata: Map<String, Any>? = null
)

/**
 * Response for related evidence.
 */
data class RelatedEvidenceResponse(
    @SerializedName("evidence_id") val evidenceId: String,
    @SerializedName("related") val related: List<RelatedEvidenceItem>,
    @SerializedName("total") val total: Int
)

data class RelatedEvidenceItem(
    @SerializedName("evidence_id") val evidenceId: String,
    @SerializedName("sha256") val sha256: String,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("relation_type") val relationType: String
)

/**
 * Ownership claim request.
 */
data class OwnershipClaimRequest(
    @SerializedName("evidence_id") val evidenceId: String,
    @SerializedName("claimant_identity") val claimantIdentity: String,
    @SerializedName("claim_type") val claimType: String,
    @SerializedName("supporting_data") val supportingData: Map<String, Any>? = null
)

/**
 * Ownership claim response.
 */
data class OwnershipClaim(
    @SerializedName("id") val id: String,
    @SerializedName("evidence_id") val evidenceId: String,
    @SerializedName("claimant_identity") val claimantIdentity: String,
    @SerializedName("claim_timestamp") val claimTimestamp: String,
    @SerializedName("claim_type") val claimType: String,
    @SerializedName("review_status") val reviewStatus: String,
    @SerializedName("review_notes") val reviewNotes: String? = null,
    @SerializedName("reviewed_at") val reviewedAt: String? = null,
    @SerializedName("reviewed_by") val reviewedBy: String? = null
)

data class OwnershipClaimListResponse(
    @SerializedName("evidence_id") val evidenceId: String,
    @SerializedName("claims") val claims: List<OwnershipClaim>
)

data class OwnershipClaimReviewRequest(
    @SerializedName("review_status") val reviewStatus: String,
    @SerializedName("review_notes") val reviewNotes: String? = null
)

/**
 * Premium backup/restore requests.
 */
data class BackupRequest(
    @SerializedName("evidence_id") val evidenceId: String,
    @SerializedName("encrypted_data") val encryptedData: String, // Base64
    @SerializedName("encryption_metadata") val encryptionMetadata: Map<String, Any>? = null
)

data class BackupResponse(
    @SerializedName("backup_id") val backupId: String,
    @SerializedName("evidence_id") val evidenceId: String,
    @SerializedName("status") val status: String,
    @SerializedName("stored_at") val storedAt: String
)

data class RestoreRequest(
    @SerializedName("evidence_id") val evidenceId: String
)

data class RestoreResponse(
    @SerializedName("evidence_id") val evidenceId: String,
    @SerializedName("encrypted_data") val encryptedData: String, // Base64
    @SerializedName("encryption_metadata") val encryptionMetadata: Map<String, Any>? = null,
    @SerializedName("restored_at") val restoredAt: String
)

data class PremiumStatus(
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("storage_used") val storageUsed: Long,
    @SerializedName("storage_limit") val storageLimit: Long,
    @SerializedName("backup_count") val backupCount: Int,
    @SerializedName("expiry") val expiry: String? = null
)

/**
 * Audit export.
 */
data class AuditExportResponse(
    @SerializedName("events") val events: List<AuditEvent>,
    @SerializedName("total") val total: Int,
    @SerializedName("export_format") val exportFormat: String
)

data class AuditEvent(
    @SerializedName("event_id") val eventId: String,
    @SerializedName("event_type") val eventType: String,
    @SerializedName("actor") val actor: String,
    @SerializedName("target") val target: String,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("policy_version") val policyVersion: String,
    @SerializedName("metadata") val metadata: Map<String, Any>? = null
)

/**
 * Policy version response.
 */
data class PolicyVersionResponse(
    @SerializedName("policy_version") val policyVersion: String,
    @SerializedName("supported_modes") val supportedModes: List<String>,
    @SerializedName("default_mode") val defaultMode: String,
    @SerializedName("similarity_thresholds") val similarityThresholds: SimilarityThresholds,
    @SerializedName("algorithm_versions") val algorithmVersions: Map<String, String>
)

/**
 * Evidence record returned from server (subset of CEE).
 */
data class EvidenceRecord(
    @SerializedName("evidence_id") val evidenceId: String,
    @SerializedName("owner_account_id") val ownerAccountId: String? = null,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("sha256_original") val sha256Original: String,
    @SerializedName("canonical_media_hash") val canonicalMediaHash: String? = null,
    @SerializedName("fingerprint_profile") val fingerprintProfile: RobustFingerprint,
    @SerializedName("fingerprint_algorithm_versions") val fingerprintAlgorithmVersions: FingerprintAlgorithmVersions,
    @SerializedName("signer_device_key_id") val signerDeviceKeyId: String,
    @SerializedName("timestamp_references") val timestampReferences: Map<String, Any>? = null,
    @SerializedName("privacy_flags") val privacyFlags: PrivacyFlags,
    @SerializedName("lifecycle_state") val lifecycleState: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)
