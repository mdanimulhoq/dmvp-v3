/**
 * DMVP v4.0 — Ownership Certificate Data Models
 */

package com.dmvp.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Ownership certificate with hybrid signatures
 */
data class OwnershipCertificate(
    @SerializedName("certificate_id")
    val certificateId: String,

    @SerializedName("evidence_id")
    val evidenceId: String,

    @SerializedName("uaid")
    val uaid: String?,

    @SerializedName("sha256")
    val sha256: String,

    @SerializedName("media_type")
    val mediaType: String,

    @SerializedName("device_key_id")
    val deviceKeyId: String,

    @SerializedName("issued_at")
    val issuedAt: Long,

    @SerializedName("valid_until")
    val validUntil: Long,

    @SerializedName("version")
    val version: String,

    @SerializedName("classical_sig")
    val classicalSig: String,

    @SerializedName("classical_algorithm")
    val classicalAlgorithm: String,

    @SerializedName("pq_sig")
    val pqSig: String,

    @SerializedName("pq_algorithm")
    val pqAlgorithm: String, // "ML-DSA-65" — NEVER "CRYSTALS-Dilithium"

    @SerializedName("hybrid_algorithm")
    val hybridAlgorithm: String
)

/**
 * Ownership claim request
 */
data class OwnershipClaimRequest(
    @SerializedName("uaid")
    val uaid: String,

    @SerializedName("claimant_identity")
    val claimantIdentity: String,

    @SerializedName("claim_type")
    val claimType: String,

    @SerializedName("claim_statement")
    val claimStatement: String? = null,

    @SerializedName("classical_sig")
    val classicalSig: String,

    @SerializedName("pq_sig")
    val pqSig: String,

    @SerializedName("classical_algorithm")
    val classicalAlgorithm: String = "Ed25519",

    @SerializedName("pq_algorithm")
    val pqAlgorithm: String = "ML-DSA-65"
)

/**
 * Ownership claim response
 */
data class OwnershipClaimResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("claim_id")
    val claimId: String,

    @SerializedName("uaid")
    val uaid: String,

    @SerializedName("claimant_identity")
    val claimantIdentity: String,

    @SerializedName("claim_type")
    val claimType: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("classical_algorithm")
    val classicalAlgorithm: String,

    @SerializedName("pq_algorithm")
    val pqAlgorithm: String,

    @SerializedName("created_at")
    val createdAt: String
)

/**
 * Claim dispute request
 */
data class ClaimDisputeRequest(
    @SerializedName("disputant_identity")
    val disputantIdentity: String,

    @SerializedName("dispute_reason")
    val disputeReason: String,

    @SerializedName("evidence")
    val evidence: String? = null,

    @SerializedName("classical_sig")
    val classicalSig: String,

    @SerializedName("pq_sig")
    val pqSig: String
)

/**
 * Ownership transfer request
 */
data class OwnershipTransferRequest(
    @SerializedName("new_owner_identity")
    val newOwnerIdentity: String,

    @SerializedName("transfer_reason")
    val transferReason: String? = null,

    @SerializedName("classical_sig")
    val classicalSig: String,

    @SerializedName("pq_sig")
    val pqSig: String
)

/**
 * Asset claims response
 */
data class AssetClaimsResponse(
    @SerializedName("uaid")
    val uaid: String,

    @SerializedName("asset")
    val asset: AssetInfo,

    @SerializedName("claim_count")
    val claimCount: Int,

    @SerializedName("claims")
    val claims: List<ClaimInfo>
)

data class AssetInfo(
    @SerializedName("sha256")
    val sha256: String,

    @SerializedName("media_type")
    val mediaType: String
)

data class ClaimInfo(
    @SerializedName("claim_id")
    val claimId: String,

    @SerializedName("claimant_identity")
    val claimantIdentity: String,

    @SerializedName("claim_type")
    val claimType: String,

    @SerializedName("claim_statement")
    val claimStatement: String?,

    @SerializedName("status")
    val status: String,

    @SerializedName("classical_algorithm")
    val classicalAlgorithm: String,

    @SerializedName("pq_algorithm")
    val pqAlgorithm: String,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("updated_at")
    val updatedAt: String,

    @SerializedName("revoked_at")
    val revokedAt: String?
)
