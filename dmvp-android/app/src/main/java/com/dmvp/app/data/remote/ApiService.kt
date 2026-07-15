package com.dmvp.app.data.remote

import com.dmvp.app.data.model.*
import com.google.gson.annotations.SerializedName
import retrofit2.http.*

interface ApiService {

    // ============================
    // Evidence Endpoints
    // ============================

    @POST("evidence")
    suspend fun registerEvidence(
        @Header("Idempotency-Key") idempotencyKey: String? = null,
        @Header("X-Request-Signature") signature: String,
        @Header("X-Nonce") nonce: String,
        @Header("X-Timestamp") timestamp: String,
        @Header("X-Policy-Version") policyVersion: String? = null,
        @Header("X-DMVP-Device-Key-Id") deviceKeyId: String,
        @Body cee: CEE
    ): ApiResponse<EvidenceRecord>

    @GET("evidence/{id}")
    suspend fun getEvidenceById(
        @Path("id") evidenceId: String,
        @Header("Authorization") auth: String
    ): EvidenceRecord

    @GET("evidence/by-hash/{sha256}")
    suspend fun getEvidenceByHash(
        @Path("sha256") sha256: String,
        @Header("Authorization") auth: String
    ): EvidenceRecord

    // ============================
    // Verification Endpoints
    // ============================

    @POST("verify")
    suspend fun verifyMedia(
        @Header("Authorization") auth: String,
        @Body request: VerificationRequest
    ): MultiAxisVerdict

    @GET("verify/policy")
    suspend fun getVerificationPolicy(
        @Header("Authorization") auth: String
    ): VerificationPolicy

    // ============================
    // Search Endpoints
    // ============================

    @POST("search")
    suspend fun searchEvidence(
        @Header("Authorization") auth: String,
        @Body request: SearchRequest
    ): SearchResponse

    @GET("search/related/{evidence_id}")
    suspend fun getRelatedEvidence(
        @Path("evidence_id") evidenceId: String,
        @Header("Authorization") auth: String,
        @Query("maxResults") maxResults: Int = 10
    ): RelatedEvidenceResponse

    // ============================
    // Device Lifecycle Endpoints
    // ============================

    @POST("devices/register")
    suspend fun registerDevice(
        @Body request: DeviceRegistrationRequest
    ): DeviceKey

    @POST("devices/rotate/{device_key_id}")
    suspend fun rotateDeviceKey(
        @Path("device_key_id") deviceKeyId: String,
        @Header("Authorization") auth: String,
        @Body request: DeviceRotationRequest
    ): DeviceKey

    @POST("devices/revoke/{device_key_id}")
    suspend fun revokeDeviceKey(
        @Path("device_key_id") deviceKeyId: String,
        @Header("Authorization") auth: String
    ): DeviceKey

    @POST("devices/recover")
    suspend fun recoverDeviceLineage(
        @Header("Authorization") auth: String,
        @Body request: DeviceRecoveryRequest
    ): DeviceKey

    @GET("devices/{device_key_id}")
    suspend fun getDeviceKey(
        @Path("device_key_id") deviceKeyId: String,
        @Header("Authorization") auth: String
    ): DeviceKey

    @GET("devices")
    suspend fun listDeviceKeys(
        @Query("trust_tier") trustTier: String? = null,
        @Query("lifecycle_state") lifecycleState: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): DeviceListResponse

    // ============================
    // Ownership Claim Endpoints
    // ============================

    @POST("ownership/claim")
    suspend fun submitOwnershipClaim(
        @Header("Authorization") auth: String,
        @Body request: OwnershipClaimRequest
    ): OwnershipClaim

    @GET("ownership/{evidence_id}")
    suspend fun getOwnershipClaims(
        @Path("evidence_id") evidenceId: String,
        @Header("Authorization") auth: String
    ): OwnershipClaimListResponse

    @GET("ownership/{evidence_id}/claim/{claim_id}")
    suspend fun getOwnershipClaim(
        @Path("evidence_id") evidenceId: String,
        @Path("claim_id") claimId: String,
        @Header("Authorization") auth: String
    ): OwnershipClaim

    @PUT("ownership/{evidence_id}/claim/{claim_id}/review")
    suspend fun reviewOwnershipClaim(
        @Path("evidence_id") evidenceId: String,
        @Path("claim_id") claimId: String,
        @Header("Authorization") auth: String,
        @Body request: OwnershipClaimReviewRequest
    ): OwnershipClaim

    // ============================
    // Premium Storage Endpoints
    // ============================

    @POST("premium/backup")
    suspend fun backupMedia(
        @Header("Authorization") auth: String,
        @Body request: BackupRequest
    ): BackupResponse

    @POST("premium/restore")
    suspend fun restoreMedia(
        @Header("Authorization") auth: String,
        @Body request: RestoreRequest
    ): RestoreResponse

    @GET("premium/status")
    suspend fun getPremiumStatus(
        @Header("Authorization") auth: String
    ): PremiumStatus

    // ============================
    // Audit and Admin Endpoints
    // ============================

    @GET("audit/export")
    suspend fun exportAuditLogs(
        @Header("Authorization") auth: String,
        @Query("start") startTime: String? = null,
        @Query("end") endTime: String? = null,
        @Query("format") format: String = "json"
    ): AuditExportResponse

    @GET("policies/version")
    suspend fun getPolicyVersion(
        @Header("Authorization") auth: String
    ): PolicyVersionResponse
}

// ================================
// Request/Response DTOs
// ================================

// ── Step 6.1: Added timestamp_mode field ──
data class VerificationRequest(
    @SerializedName("sha256") val sha256: String,
    @SerializedName("canonical_media_hash") val canonicalMediaHash: String? = null,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("robust_fingerprint_profile") val robustFingerprintProfile: RobustFingerprint? = null,
    @SerializedName("verification_mode") val verificationMode: String = "standard",
    @SerializedName("signer_device_key_id") val signerDeviceKeyId: String? = null,
    @SerializedName("timestamp_info") val timestampInfo: Map<String, Any>? = null,
    @SerializedName("timestamp_mode") val timestampMode: String = "standard"
)

data class SearchRequest(
    @SerializedName("sha256") val sha256: String,
    @SerializedName("canonical_media_hash") val canonicalMediaHash: String? = null,
    @SerializedName("robust_fingerprint_profile") val robustFingerprintProfile: RobustFingerprint? = null,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("filters") val filters: Map<String, String>? = null,
    @SerializedName("maxResults") val maxResults: Int = 10,
    @SerializedName("maxCandidates") val maxCandidates: Int = 100
)

data class SearchResponse(
    @SerializedName("matched_evidence") val matchedEvidence: List<MatchedEvidence>,
    @SerializedName("total_matches") val totalMatches: Int,
    @SerializedName("best_match_type") val bestMatchType: String,
    @SerializedName("best_score") val bestScore: Double,
    @SerializedName("search_metadata") val searchMetadata: Map<String, Any>? = null
)

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

data class OwnershipClaimRequest(
    @SerializedName("evidence_id") val evidenceId: String,
    @SerializedName("claimant_identity") val claimantIdentity: String,
    @SerializedName("claim_type") val claimType: String,
    @SerializedName("supporting_data") val supportingData: Map<String, Any>? = null
)

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

data class BackupRequest(
    @SerializedName("evidence_id") val evidenceId: String,
    @SerializedName("encrypted_data") val encryptedData: String,
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
    @SerializedName("encrypted_data") val encryptedData: String,
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

data class PolicyVersionResponse(
    @SerializedName("policy_version") val policyVersion: String,
    @SerializedName("supported_modes") val supportedModes: List<String>,
    @SerializedName("default_mode") val defaultMode: String,
    @SerializedName("similarity_thresholds") val similarityThresholds: SimilarityThresholds,
    @SerializedName("algorithm_versions") val algorithmVersions: Map<String, String>
)

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
