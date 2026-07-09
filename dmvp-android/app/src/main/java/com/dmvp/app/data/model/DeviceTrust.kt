/**
 * app/src/main/java/com/dmvp/app/data/model/DeviceTrust.kt
 *
 * Device trust and lifecycle data models for DMVP v3.0.
 * Represents device trust tiers, key lifecycle states,
 * attestation data, and lineage information.
 * Used for device registration, rotation, revocation, and recovery.
 *
 * Uses Gson annotations for JSON serialization/deserialization.
 */

package com.dmvp.app.data.model

import com.google.gson.annotations.SerializedName
import java.util.Date

/**
 * Device trust tiers as defined in the specification.
 */
enum class DeviceTrustTier {
    @SerializedName("TIER_A")
    TIER_A,  // Hardware-backed key + valid attestation

    @SerializedName("TIER_B")
    TIER_B,  // Hardware-backed key, attestation unavailable/degraded

    @SerializedName("TIER_C")
    TIER_C,  // Software-backed key or desktop secure store

    @SerializedName("TIER_D")
    TIER_D   // Revoked or untrusted device
}

/**
 * Device lifecycle states.
 */
enum class DeviceLifecycleState {
    @SerializedName("ACTIVE")
    ACTIVE,

    @SerializedName("ROTATED")
    ROTATED,

    @SerializedName("REVOKED")
    REVOKED,

    @SerializedName("RECOVERED")
    RECOVERED
}

/**
 * Device key information returned from the API.
 */
data class DeviceKey(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("device_key_id")
    val deviceKeyId: String,

    @SerializedName("public_key")
    val publicKey: String,

    @SerializedName("trust_tier")
    val trustTier: DeviceTrustTier,

    @SerializedName("lifecycle_state")
    val lifecycleState: DeviceLifecycleState,

    @SerializedName("revoked_at")
    val revokedAt: String? = null,

    @SerializedName("lineage_parent_id")
    val lineageParentId: String? = null,

    @SerializedName("created_at")
    val createdAt: String? = null,

    @SerializedName("updated_at")
    val updatedAt: String? = null
)

/**
 * Request body for device registration.
 */
data class DeviceRegistrationRequest(
    @SerializedName("device_key_id")
    val deviceKeyId: String,

    @SerializedName("public_key")
    val publicKey: String,

    @SerializedName("attestation_summary")
    val attestationSummary: AttestationSummary? = null,

    @SerializedName("platform")
    val platform: String = "android"
)

/**
 * Request body for device rotation.
 */
data class DeviceRotationRequest(
    @SerializedName("new_device_key_id")
    val newDeviceKeyId: String,

    @SerializedName("new_public_key")
    val newPublicKey: String,

    @SerializedName("attestation_summary")
    val attestationSummary: AttestationSummary? = null,

    @SerializedName("platform")
    val platform: String = "android"
)

/**
 * Request body for device recovery.
 */
data class DeviceRecoveryRequest(
    @SerializedName("old_device_key_id")
    val oldDeviceKeyId: String,

    @SerializedName("new_device_key_id")
    val newDeviceKeyId: String,

    @SerializedName("new_public_key")
    val newPublicKey: String,

    @SerializedName("attestation_summary")
    val attestationSummary: AttestationSummary? = null,

    @SerializedName("platform")
    val platform: String = "android",

    @SerializedName("recovery_quorum")
    val recoveryQuorum: String? = null
)

/**
 * Response for device list (paginated).
 */
data class DeviceListResponse(
    @SerializedName("items")
    val items: List<DeviceKey>,

    @SerializedName("total")
    val total: Int,

    @SerializedName("page")
    val page: Int,

    @SerializedName("limit")
    val limit: Int
)

/**
 * Local device trust state stored on the device.
 * Includes the local key ID, trust tier, and attestation summary.
 */
data class LocalDeviceTrustState(
    // The device key ID that we are using (may change after rotation/recovery)
    val currentDeviceKeyId: String,

    // The public key (for verification)
    val publicKey: String,

    // Trust tier of the current device
    val trustTier: DeviceTrustTier,

    // Whether attestation is available
    val attestationAvailable: Boolean,

    // Attestation summary (if available)
    val attestationSummary: AttestationSummary? = null,

    // List of all known device keys (for lineage tracking)
    val knownDeviceKeys: List<DeviceKey> = emptyList(),

    // Timestamp of last refresh
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Helper extension functions for DeviceTrustTier.
 */
fun DeviceTrustTier.getDisplayName(): String {
    return when (this) {
        DeviceTrustTier.TIER_A -> "Tier A - Hardware Secure"
        DeviceTrustTier.TIER_B -> "Tier B - Hardware Key"
        DeviceTrustTier.TIER_C -> "Tier C - Software Key"
        DeviceTrustTier.TIER_D -> "Tier D - Revoked"
    }
}

fun DeviceTrustTier.getBadgeColor(): Int {
    // Return color resource IDs; to be used in Compose with color values from theme.
    // We'll use the color constants from Color.kt.
    return when (this) {
        DeviceTrustTier.TIER_A -> 0xFF00E676.toInt()  // green
        DeviceTrustTier.TIER_B -> 0xFFFFD740.toInt()  // amber
        DeviceTrustTier.TIER_C -> 0xFFFF6D00.toInt()  // orange
        DeviceTrustTier.TIER_D -> 0xFFE53935.toInt()  // red
    }
}

fun DeviceTrustTier.getEvidenceQuality(): EvidenceQualityVerdict {
    return when (this) {
        DeviceTrustTier.TIER_A -> EvidenceQualityVerdict.HIGH_EVIDENTIARY_STRENGTH
        DeviceTrustTier.TIER_B -> EvidenceQualityVerdict.MODERATE_EVIDENTIARY_STRENGTH
        DeviceTrustTier.TIER_C -> EvidenceQualityVerdict.LOW_EVIDENTIARY_STRENGTH
        DeviceTrustTier.TIER_D -> EvidenceQualityVerdict.LOW_EVIDENTIARY_STRENGTH
    }
}

/**
 * Helper for DeviceLifecycleState.
 */
fun DeviceLifecycleState.getDisplayName(): String {
    return when (this) {
        DeviceLifecycleState.ACTIVE -> "Active"
        DeviceLifecycleState.ROTATED -> "Rotated"
        DeviceLifecycleState.REVOKED -> "Revoked"
        DeviceLifecycleState.RECOVERED -> "Recovered"
    }
}
