/**
 * app/src/main/java/com/dmvp/app/utils/CEEBuilder.kt
 *
 * Canonical Evidence Envelope (CEE) builder utility for DMVP v3.0 Android app.
 * Provides a fluent builder pattern for constructing CEE objects from media files.
 *
 * Handles:
 *   - Media type detection
 *   - SHA-256 hash computation
 *   - Canonical media hash computation (optional)
 *   - Robust fingerprint generation (image or video)
 *   - Device key signing
 *   - Privacy flags management
 *   - Timestamp generation
 *
 * Usage:
 *   val cee = CEEBuilder.buildFromImageFile(
 *       context = context,
 *       imageFile = file,
 *       deviceKeyId = "device-id",
 *       publicKeyRef = "public-key",
 *       privacyFlags = PrivacyFlags(gps = false, exif = false, deviceInfo = true)
 *   )
 *
 *   // For video:
 *   val cee = CEEBuilder.buildFromVideoFile(...)
 */

package com.dmvp.app.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.dmvp.app.data.model.*
import com.dmvp.app.security.DeviceKeyManager
import com.dmvp.app.security.FingerprintUtils
import com.dmvp.app.security.HashUtils
import com.dmvp.app.security.SignatureUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import timber.log.Timber

private const val TAG = "CEEBuilder"
private const val PROTOCOL_VERSION = "dmvp-v3.0.0"
private const val CLIENT_APP_VERSION = "3.0.0"
private const val VERIFICATION_POLICY_VERSION = "dmvp-v3.0.0"
private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

/**
 * CEE Builder object for constructing Canonical Evidence Envelopes.
 */
object CEEBuilder {

    private val iso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Build a CEE from an image file.
     *
     * @param context Android context.
     * @param imageFile The image file.
     * @param deviceKeyId The device key identifier.
     * @param publicKeyRef The public key reference (public key base64).
     * @param privacyFlags Privacy flags for metadata collection.
     * @param captureTimeClaim Optional capture time claim (ISO 8601).
     * @param geolocationClaim Optional geolocation claim.
     * @param chainParentEvidenceId Optional parent evidence ID for lineage.
     * @param includeCanonicalHash Whether to compute canonical hash (default: true).
     * @return CEE object or null if build fails.
     */
    fun buildFromImageFile(
        context: Context,
        imageFile: File,
        deviceKeyId: String,
        publicKeyRef: String,
        privacyFlags: PrivacyFlags = PrivacyFlags(),
        captureTimeClaim: String? = null,
        geolocationClaim: GeolocationClaim? = null,
        chainParentEvidenceId: String? = null,
        includeCanonicalHash: Boolean = true
    ): CEE? {
        return try {
            // Validate file exists and is readable
            if (!imageFile.exists() || !imageFile.canRead()) {
                Timber.e("Image file does not exist or cannot be read: ${imageFile.absolutePath}")
                return null
            }

            // Compute SHA-256
            val sha256Original = HashUtils.sha256(imageFile)

            // Compute canonical hash (optional)
            val canonicalHash = if (includeCanonicalHash) {
                HashUtils.canonicalHash(imageFile, "image")
            } else {
                null
            }

            // Generate robust fingerprint
            val fingerprint = FingerprintUtils.generateImageFingerprint(imageFile.absolutePath)
            if (fingerprint == null) {
                Timber.e("Failed to generate fingerprint for image")
                return null
            }

            // Get attestation summary from DeviceKeyManager
            val attestationSummary = DeviceKeyManager.getAttestationSummary()
            val deviceAttestation = AttestationSummary(
                valid = attestationSummary["valid"] as? Boolean ?: true,
                hardwareBacked = attestationSummary["hardware_backed"] as? Boolean ?: false,
                platform = "android",
                appIntegrity = true, // For MVP, assume app integrity is valid
                rooted = false, // Could check with SafetyNet/Play Integrity
                extra = attestationSummary.mapKeys { it.key } as? Map<String, Any>
            )

            // Build the CEE
            buildCEE(
                mediaType = "image",
                sha256Original = sha256Original,
                canonicalMediaHash = canonicalHash,
                robustFingerprint = fingerprint,
                signerDeviceKeyId = deviceKeyId,
                signerPublicKeyRef = publicKeyRef,
                deviceAttestation = deviceAttestation,
                privacyFlags = privacyFlags,
                captureTimeClaim = captureTimeClaim,
                geolocationClaim = geolocationClaim,
                chainParentEvidenceId = chainParentEvidenceId
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to build CEE from image file")
            null
        }
    }

    /**
     * Build a CEE from a video file.
     *
     * @param context Android context.
     * @param videoFile The video file.
     * @param deviceKeyId The device key identifier.
     * @param publicKeyRef The public key reference.
     * @param privacyFlags Privacy flags for metadata collection.
     * @param captureTimeClaim Optional capture time claim.
     * @param geolocationClaim Optional geolocation claim.
     * @param chainParentEvidenceId Optional parent evidence ID.
     * @param maxKeyframes Maximum keyframes to extract.
     * @return CEE object or null if build fails.
     */
    fun buildFromVideoFile(
        context: Context,
        videoFile: File,
        deviceKeyId: String,
        publicKeyRef: String,
        privacyFlags: PrivacyFlags = PrivacyFlags(),
        captureTimeClaim: String? = null,
        geolocationClaim: GeolocationClaim? = null,
        chainParentEvidenceId: String? = null,
        maxKeyframes: Int = 10
    ): CEE? {
        return try {
            // Validate file exists and is readable
            if (!videoFile.exists() || !videoFile.canRead()) {
                Timber.e("Video file does not exist or cannot be read: ${videoFile.absolutePath}")
                return null
            }

            // Compute SHA-256
            val sha256Original = HashUtils.sha256(videoFile)

            // Canonical hash for video (not implemented for MVP)
            val canonicalHash: String? = null // HashUtils.canonicalHash(videoFile, "video")

            // Generate robust fingerprint
            val fingerprint = FingerprintUtils.generateVideoFingerprint(
                videoFile.absolutePath,
                maxKeyframes = maxKeyframes
            )
            if (fingerprint == null) {
                Timber.e("Failed to generate fingerprint for video")
                return null
            }

            // Get attestation summary
            val attestationSummary = DeviceKeyManager.getAttestationSummary()
            val deviceAttestation = AttestationSummary(
                valid = attestationSummary["valid"] as? Boolean ?: true,
                hardwareBacked = attestationSummary["hardware_backed"] as? Boolean ?: false,
                platform = "android",
                appIntegrity = true,
                rooted = false,
                extra = attestationSummary.mapKeys { it.key } as? Map<String, Any>
            )

            // Build the CEE
            buildCEE(
                mediaType = "video",
                sha256Original = sha256Original,
                canonicalMediaHash = canonicalHash,
                robustFingerprint = fingerprint,
                signerDeviceKeyId = deviceKeyId,
                signerPublicKeyRef = publicKeyRef,
                deviceAttestation = deviceAttestation,
                privacyFlags = privacyFlags,
                captureTimeClaim = captureTimeClaim,
                geolocationClaim = geolocationClaim,
                chainParentEvidenceId = chainParentEvidenceId
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to build CEE from video file")
            null
        }
    }

    /**
     * Build a CEE from a byte array (for images only, for MVP).
     *
     * @param imageData Byte array containing image data.
     * @param deviceKeyId The device key identifier.
     * @param publicKeyRef The public key reference.
     * @param privacyFlags Privacy flags.
     * @param captureTimeClaim Optional capture time claim.
     * @param geolocationClaim Optional geolocation claim.
     * @param chainParentEvidenceId Optional parent evidence ID.
     * @return CEE object or null if build fails.
     */
    fun buildFromImageBytes(
        imageData: ByteArray,
        deviceKeyId: String,
        publicKeyRef: String,
        privacyFlags: PrivacyFlags = PrivacyFlags(),
        captureTimeClaim: String? = null,
        geolocationClaim: GeolocationClaim? = null,
        chainParentEvidenceId: String? = null
    ): CEE? {
        return try {
            // Compute SHA-256
            val sha256Original = HashUtils.sha256(imageData)

            // Canonical hash (not from bytes, would need to decode first)
            val canonicalHash: String? = null

            // Generate fingerprint
            val fingerprint = FingerprintUtils.generateFingerprintFromBytes(
                imageData,
                "image"
            )
            if (fingerprint == null) {
                Timber.e("Failed to generate fingerprint from image bytes")
                return null
            }

            // Attestation summary
            val attestationSummary = DeviceKeyManager.getAttestationSummary()
            val deviceAttestation = AttestationSummary(
                valid = attestationSummary["valid"] as? Boolean ?: true,
                hardwareBacked = attestationSummary["hardware_backed"] as? Boolean ?: false,
                platform = "android",
                appIntegrity = true,
                rooted = false,
                extra = attestationSummary.mapKeys { it.key } as? Map<String, Any>
            )

            buildCEE(
                mediaType = "image",
                sha256Original = sha256Original,
                canonicalMediaHash = canonicalHash,
                robustFingerprint = fingerprint,
                signerDeviceKeyId = deviceKeyId,
                signerPublicKeyRef = publicKeyRef,
                deviceAttestation = deviceAttestation,
                privacyFlags = privacyFlags,
                captureTimeClaim = captureTimeClaim,
                geolocationClaim = geolocationClaim,
                chainParentEvidenceId = chainParentEvidenceId
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to build CEE from image bytes")
            null
        }
    }

    /**
     * Core CEE building function.
     * Constructs the CEE object, canonicalizes it, signs it, and returns the complete envelope.
     */
    private fun buildCEE(
        mediaType: String,
        sha256Original: String,
        canonicalMediaHash: String?,
        robustFingerprint: RobustFingerprint,
        signerDeviceKeyId: String,
        signerPublicKeyRef: String,
        deviceAttestation: AttestationSummary?,
        privacyFlags: PrivacyFlags,
        captureTimeClaim: String?,
        geolocationClaim: GeolocationClaim?,
        chainParentEvidenceId: String?
    ): CEE? {
        try {
            // Generate evidence ID
            val evidenceId = UUID.randomUUID().toString()

            // Registration server time (will be set by server, but we include a placeholder)
            val registrationServerTime = iso8601Format.format(Date())

            // Audit reference
            val auditReference = "android-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"

            // ── Step 4.3: Set fingerprint algorithm version ──
            val fingerprintAlgorithmVersions = FingerprintAlgorithmVersions(
                fingerprint = "phash-dct-v1",
                similarity = "hamming-v1",
                normalization = "jpeg-baseline-v1"
            )

            // Build the CEE without signature
            val ceeUnsigned = CEE(
                protocolVersion = PROTOCOL_VERSION,
                evidenceId = evidenceId,
                mediaType = mediaType,
                sha256Original = sha256Original,
                canonicalMediaHash = canonicalMediaHash,
                robustFingerprintProfile = robustFingerprint,
                fingerprintAlgorithmVersions = fingerprintAlgorithmVersions,
                signerDeviceKeyId = signerDeviceKeyId,
                signerPublicKeyReference = signerPublicKeyRef,
                signatureAlgorithm = SIGNATURE_ALGORITHM,
                deviceAttestationSummary = deviceAttestation,
                registrationServerTime = registrationServerTime,
                trustedTimestampTokenReference = null, // Not supported in MVP
                captureTimeClaim = captureTimeClaim,
                geolocationClaim = geolocationClaim,
                privacyFlags = privacyFlags,
                clientAppVersion = CLIENT_APP_VERSION,
                verificationPolicyVersion = VERIFICATION_POLICY_VERSION,
                chainParentEvidenceId = chainParentEvidenceId,
                auditReference = auditReference,
                signature = "" // Placeholder, will be filled after signing
            )

            // Sign the canonical representation
            val signature = SignatureUtils.signPayload(ceeUnsigned)
            if (signature == null) {
                Timber.e("Failed to sign CEE")
                return null
            }

            // Return the complete CEE with signature
            return ceeUnsigned.copy(signature = signature)

        } catch (e: Exception) {
            Timber.e(e, "Failed to build CEE")
            return null
        }
    }

    /**
     * Build a CEE with provided SHA-256 hash (for cases where hash is already computed).
     */
    fun buildFromHash(
        sha256Original: String,
        mediaType: String,
        robustFingerprint: RobustFingerprint,
        signerDeviceKeyId: String,
        signerPublicKeyRef: String,
        privacyFlags: PrivacyFlags = PrivacyFlags(),
        captureTimeClaim: String? = null,
        geolocationClaim: GeolocationClaim? = null,
        chainParentEvidenceId: String? = null,
        canonicalMediaHash: String? = null
    ): CEE? {
        return try {
            val attestationSummary = DeviceKeyManager.getAttestationSummary()
            val deviceAttestation = AttestationSummary(
                valid = attestationSummary["valid"] as? Boolean ?: true,
                hardwareBacked = attestationSummary["hardware_backed"] as? Boolean ?: false,
                platform = "android",
                appIntegrity = true,
                rooted = false,
                extra = attestationSummary.mapKeys { it.key } as? Map<String, Any>
            )

            buildCEE(
                mediaType = mediaType,
                sha256Original = sha256Original,
                canonicalMediaHash = canonicalMediaHash,
                robustFingerprint = robustFingerprint,
                signerDeviceKeyId = signerDeviceKeyId,
                signerPublicKeyRef = signerPublicKeyRef,
                deviceAttestation = deviceAttestation,
                privacyFlags = privacyFlags,
                captureTimeClaim = captureTimeClaim,
                geolocationClaim = geolocationClaim,
                chainParentEvidenceId = chainParentEvidenceId
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to build CEE from hash")
            null
        }
    }

    /**
     * Create a CEE for a test/derivative evidence (without actual media).
     * Used for chain linking or ownership claims.
     */
    fun buildDerivativeEvidence(
        parentEvidenceId: String,
        signerDeviceKeyId: String,
        signerPublicKeyRef: String,
        sha256Original: String? = null,
        mediaType: String = "image",
        privacyFlags: PrivacyFlags = PrivacyFlags(),
        captureTimeClaim: String? = null
    ): CEE? {
        return try {
            // For derivative, we use a minimal fingerprint
            val minimalFingerprint = RobustFingerprint(
                phash = "",
                dhash = null,
                blockHash = null
            )

            val attestationSummary = DeviceKeyManager.getAttestationSummary()
            val deviceAttestation = AttestationSummary(
                valid = attestationSummary["valid"] as? Boolean ?: true,
                hardwareBacked = attestationSummary["hardware_backed"] as? Boolean ?: false,
                platform = "android",
                appIntegrity = true,
                rooted = false,
                extra = attestationSummary.mapKeys { it.key } as? Map<String, Any>
            )

            buildCEE(
                mediaType = mediaType,
                sha256Original = sha256Original ?: "0000000000000000000000000000000000000000000000000000000000000000",
                canonicalMediaHash = null,
                robustFingerprint = minimalFingerprint,
                signerDeviceKeyId = signerDeviceKeyId,
                signerPublicKeyRef = signerPublicKeyRef,
                deviceAttestation = deviceAttestation,
                privacyFlags = privacyFlags,
                captureTimeClaim = captureTimeClaim,
                geolocationClaim = null,
                chainParentEvidenceId = parentEvidenceId
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to build derivative evidence")
            null
        }
    }
}
