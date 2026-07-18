/**
 * app/src/main/java/com/dmvp/app/data/model/CEE.kt
 *
 * Canonical Evidence Envelope (CEE) data model for DMVP v3.0.
 * Represents the core registry object for evidence registration.
 * Includes all required fields as per specification.
 * Uses Gson annotations for JSON serialization/deserialization.
 */

package com.dmvp.app.data.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * Canonical Evidence Envelope.
 * This is the immutable, versioned registry object for every evidence registration.
 */
data class CEE(
    // Protocol version (e.g., "dmvp-v3.0.0")
    @SerializedName("protocol_version")
    val protocolVersion: String,

    // Unique evidence identifier (UUID)
    @SerializedName("evidence_id")
    val evidenceId: String = UUID.randomUUID().toString(),

    // Media type: "image" or "video"
    @SerializedName("media_type")
    val mediaType: String,

    // SHA-256 hash of the original file bytes (64-character hex)
    @SerializedName("sha256_original")
    val sha256Original: String,

    // ── TDD v5 Phase 1 Step 1.4: BLAKE3 hash ──
    // BLAKE3 hash of the original file bytes (64-character hex)
    // 3-5× faster than SHA-256, used as internal primary key
    @SerializedName("blake3_hash")
    val blake3Hash: String? = null,

    // Optional canonical media hash (64-character hex) for deterministic normalization
    @SerializedName("canonical_media_hash")
    val canonicalMediaHash: String? = null,

    // Robust fingerprint profile (contains phash, dhash, blockHash, etc.)
    @SerializedName("robust_fingerprint_profile")
    val robustFingerprintProfile: RobustFingerprint,

    // Fingerprint algorithm versions
    @SerializedName("fingerprint_algorithm_versions")
    val fingerprintAlgorithmVersions: FingerprintAlgorithmVersions,

    // Device key identifier of the signer
    @SerializedName("signer_device_key_id")
    val signerDeviceKeyId: String,

    // Public key reference (or the public key itself)
    @SerializedName("signer_public_key_reference")
    val signerPublicKeyReference: String,

    // Signature algorithm used (e.g., "SHA256withECDSA")
    @SerializedName("signature_algorithm")
    val signatureAlgorithm: String = "SHA256withECDSA",

    // Device attestation summary (JSON object)
    @SerializedName("device_attestation_summary")
    val deviceAttestationSummary: AttestationSummary? = null,

    // Registration server time (ISO 8601 timestamp)
    @SerializedName("registration_server_time")
    val registrationServerTime: String,

    // Optional trusted timestamp token reference
    @SerializedName("trusted_timestamp_token_reference")
    val trustedTimestampTokenReference: String? = null,

    // Optional capture time claim (ISO 8601 timestamp from client)
    @SerializedName("capture_time_claim")
    val captureTimeClaim: String? = null,

    // Optional geolocation claim (latitude, longitude)
    @SerializedName("geolocation_claim")
    val geolocationClaim: GeolocationClaim? = null,

    // Privacy flags for metadata collection
    @SerializedName("privacy_flags")
    val privacyFlags: PrivacyFlags,

    // Client application version
    @SerializedName("client_app_version")
    val clientAppVersion: String,

    // Verification policy version
    @SerializedName("verification_policy_version")
    val verificationPolicyVersion: String,

    // Optional parent evidence ID for chain links (e.g., derivatives)
    @SerializedName("chain_parent_evidence_id")
    val chainParentEvidenceId: String? = null,

    // Audit reference (e.g., for tracking)
    @SerializedName("audit_reference")
    val auditReference: String,

    // Digital signature (base64) over the canonical serialization of the envelope
    @SerializedName("signature")
    val signature: String
)

/**
 * Device attestation summary.
 * Contains attestation data from the client platform.
 */
data class AttestationSummary(
    @SerializedName("valid")
    val valid: Boolean = false,

    @SerializedName("hardware_backed")
    val hardwareBacked: Boolean = false,

    @SerializedName("platform")
    val platform: String? = null,

    @SerializedName("app_integrity")
    val appIntegrity: Boolean? = null,

    @SerializedName("rooted")
    val rooted: Boolean? = null,

    // Additional attestation data as key-value pairs
    @SerializedName("extra")
    val extra: Map<String, Any>? = null
)

/**
 * Geolocation claim (latitude, longitude).
 */
data class GeolocationClaim(
    @SerializedName("lat")
    val lat: Double,

    @SerializedName("lng")
    val lng: Double
)

/**
 * Privacy flags for metadata collection.
 * Determines which optional metadata fields are stored.
 */
data class PrivacyFlags(
    @SerializedName("gps")
    val gps: Boolean = false,          // GPS location stored?

    @SerializedName("exif")
    val exif: Boolean = false,         // EXIF metadata stored?

    @SerializedName("device_info")
    val deviceInfo: Boolean = false    // Device descriptors stored?
)
