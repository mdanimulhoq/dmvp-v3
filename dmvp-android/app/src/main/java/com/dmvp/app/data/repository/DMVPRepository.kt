/**
 * app/src/main/java/com/dmvp/app/data/repository/DMVPRepository.kt
 *
 * Main repository for DMVP v3.0 Android app.
 * Abstracts all data operations: device key management, evidence registration,
 * verification, search, ownership claims, and premium features.
 *
 * Uses:
 *   - Retrofit ApiService for network calls.
 *   - DeviceKeyManager for key operations.
 *   - CEEBuilder for constructing Canonical Evidence Envelopes.
 *   - DataStore/Preferences for caching device state and other data.
 *   - Result<T> wrapper for all operations to handle success/error states.
 *
 * All functions are suspendable (coroutines).
 */

package com.dmvp.app.data.repository

import android.content.Context
import android.util.Log
import com.dmvp.app.data.model.*
import com.dmvp.app.data.remote.*
import com.dmvp.app.security.DeviceKeyManager
import com.dmvp.app.security.FingerprintUtils
import com.dmvp.app.security.HashUtils
import com.dmvp.app.security.SignatureUtils
import com.dmvp.app.utils.CEEBuilder
import com.dmvp.app.utils.Constants
import com.dmvp.app.utils.PrefsKeys
import com.dmvp.app.utils.currentIso8601
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val TAG = "DMVPRepository"

/**
 * Repository result wrapper.
 * Sealed class to represent success or failure.
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(
        val exception: Throwable? = null,
        val errorCode: String? = null,
        val message: String? = null
    ) : Result<Nothing>()
}

/**
 * Main repository class.
 */
class DMVPRepository(private val context: Context) {

    // API service instance
    private val apiService: ApiService by lazy {
        RetrofitClient.getInstance(context)
    }

    // ============================
    // Device Key Management
    // ============================

    /**
     * Get or create the device key.
     * If the key exists in Keystore, return it.
     * Otherwise, generate a new key and register it with the server.
     * Returns the device key ID and public key.
     */
    suspend fun getOrCreateDeviceKey(): Result<Pair<String, String>> {
        return withContext(Dispatchers.IO) {
            try {
                // Check if key exists in Keystore
                if (DeviceKeyManager.hasDeviceKey()) {
                    // Key exists, get public key
                    val publicKey = DeviceKeyManager.getPublicKey()
                    if (publicKey != null) {
                        val deviceKeyId = getDeviceKeyId() ?: generateDeviceKeyId()
                        return@withContext Result.Success(Pair(deviceKeyId, publicKey))
                    }
                }

                // No key, generate new one
                Log.d(TAG, "Generating new device key")
                val attestationChallenge = UUID.randomUUID().toString().toByteArray()
                val result = DeviceKeyManager.generateDeviceKey(context, attestationChallenge)
                if (result == null) {
                    return@withContext Result.Error(errorCode = "KEY_GENERATION_FAILED", message = "Failed to generate device key")
                }

                val (publicKeyBase64, certChain) = result
                val deviceKeyId = generateDeviceKeyId()

                // Register the device with the server
                val attestationSummary = buildAttestationSummary(certChain)
                val registrationRequest = DeviceRegistrationRequest(
                    deviceKeyId = deviceKeyId,
                    publicKey = publicKeyBase64,
                    attestationSummary = attestationSummary,
                    platform = "android"
                )

                // Store device key info locally
                saveDeviceKeyId(deviceKeyId)
                savePublicKey(publicKeyBase64)

                // Register with server
                val response = apiService.registerDevice(
                    auth = "", // TODO: handle auth if needed
                    request = registrationRequest
                )
                // Trust tier will be assigned by server, but we can cache it
                saveTrustTier(response.trustTier.name)

                Result.Success(Pair(deviceKeyId, publicKeyBase64))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get or create device key", e)
                Result.Error(exception = e, message = e.message)
            }
        }
    }
    /**
     * Get the current device key ID from local storage.
     */
    private fun getDeviceKeyId(): String? {
        // In production, use DataStore. For MVP, use SharedPreferences.
        val prefs = context.getSharedPreferences("dmvp_prefs", Context.MODE_PRIVATE)
        return prefs.getString(PrefsKeys.KEY_DEVICE_KEY_ID, null)
    }

    private fun saveDeviceKeyId(deviceKeyId: String) {
        val prefs = context.getSharedPreferences("dmvp_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PrefsKeys.KEY_DEVICE_KEY_ID, deviceKeyId).apply()
        RetrofitClient.setDeviceKeyId(deviceKeyId)
    }

    private fun savePublicKey(publicKey: String) {
        val prefs = context.getSharedPreferences("dmvp_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PrefsKeys.KEY_PUBLIC_KEY, publicKey).apply()
    }

    private fun saveTrustTier(trustTier: String) {
        val prefs = context.getSharedPreferences("dmvp_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PrefsKeys.KEY_TRUST_TIER, trustTier).apply()
    }

    private fun generateDeviceKeyId(): String {
        return "device_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    }

    /**
     * Build attestation summary from certificate chain.
     */
    private fun buildAttestationSummary(certChain: List<java.security.cert.X509Certificate>): AttestationSummary {
        return AttestationSummary(
            valid = true,
            hardwareBacked = DeviceKeyManager.isHardwareBacked(),
            platform = "android",
            appIntegrity = true,
            rooted = false,
            extra = mapOf(
                "cert_count" to certChain.size,
                "api_level" to android.os.Build.VERSION.SDK_INT
            )
        )
    }

    /**
     * Get the current device trust tier from local cache.
     */
    fun getCachedTrustTier(): String? {
        val prefs = context.getSharedPreferences("dmvp_prefs", Context.MODE_PRIVATE)
        return prefs.getString(PrefsKeys.KEY_TRUST_TIER, null)
    }

    // ============================
    // Evidence Registration
    // ============================

    /**
     * Register a media file as evidence.
     * Builds CEE, signs it, and sends to server.
     */
    suspend fun registerMedia(
        file: File,
        mediaType: String,
        privacyFlags: PrivacyFlags = PrivacyFlags(),
        captureTimeClaim: String? = null,
        geolocationClaim: GeolocationClaim? = null,
        chainParentEvidenceId: String? = null
    ): Result<EvidenceRecord> {
        return withContext(Dispatchers.IO) {
            try {
                // Ensure device key exists
                val keyResult = getOrCreateDeviceKey()
                if (keyResult is Result.Error) {
                    return@withContext Result.Error(
                        errorCode = "DEVICE_KEY_ERROR",
                        message = "Failed to get device key"
                    )
                }
                val (deviceKeyId, publicKey) = (keyResult as Result.Success).data

                // Build CEE
                val cee = when (mediaType) {
                    Constants.MEDIA_TYPE_IMAGE -> CEEBuilder.buildFromImageFile(
                        context = context,
                        imageFile = file,
                        deviceKeyId = deviceKeyId,
                        publicKeyRef = publicKey,
                        privacyFlags = privacyFlags,
                        captureTimeClaim = captureTimeClaim,
                        geolocationClaim = geolocationClaim,
                        chainParentEvidenceId = chainParentEvidenceId
                    )
                    Constants.MEDIA_TYPE_VIDEO -> CEEBuilder.buildFromVideoFile(
                        context = context,
                        videoFile = file,
                        deviceKeyId = deviceKeyId,
                        publicKeyRef = publicKey,
                        privacyFlags = privacyFlags,
                        captureTimeClaim = captureTimeClaim,
                        geolocationClaim = geolocationClaim,
                        chainParentEvidenceId = chainParentEvidenceId
                    )
                    else -> null
                }

                if (cee == null) {
                    return@withContext Result.Error(
                        errorCode = "CEE_BUILD_FAILED",
                        message = "Failed to build Canonical Evidence Envelope"
                    )
                }

                // Sign the request
                val nonce = SignatureUtils.generateNonce()
                val timestamp = currentIso8601()
                val canonicalPayload = SignatureUtils.canonicalizePayload(cee)
                val canonicalRequest = "$canonicalPayload\n$nonce\n$timestamp"
                val signature = DeviceKeyManager.signString(canonicalRequest)
                if (signature == null) {
                    return@withContext Result.Error(
                        errorCode = "SIGNING_FAILED",
                        message = "Failed to sign evidence"
                    )
                }
                // Idempotency key (optional, use SHA-256 of file as idempotency key)
                val idempotencyKey = HashUtils.sha256(file)

                // Send registration
                val response = apiService.registerEvidence(
                    idempotencyKey = idempotencyKey,
                    signature = signature,
                    nonce = nonce,
                    timestamp = timestamp,
                    policyVersion = Constants.PROTOCOL_VERSION,
                    cee = cee
                )

                if (response.data != null) {
                    Result.Success(response.data)
                } else {
                    Result.Error(
                        errorCode = "EMPTY_RESPONSE",
                        message = "Server returned empty response"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Registration failed", e)
                when (e) {
                    is ApiException -> Result.Error(
                        exception = e,
                        errorCode = e.errorCode,
                        message = e.message
                    )
                    else -> Result.Error(exception = e, message = e.message)
                }
            }
        }
    }

    // ============================
    // Verification
    // ============================

    /**
     * Verify a media file against the registry.
     */
    suspend fun verifyMedia(
        sha256: String,
        mediaType: String,
        fingerprintProfile: RobustFingerprint? = null,
        mode: String = "standard",
        canonicalHash: String? = null,
        deviceKeyId: String? = null
    ): Result<MultiAxisVerdict> {
        return withContext(Dispatchers.IO) {
            try {
                val request = VerificationRequest(
                    sha256 = sha256,
                    canonicalMediaHash = canonicalHash,
                    mediaType = mediaType,
                    robustFingerprintProfile = fingerprintProfile,
                    verificationMode = mode,
                    signerDeviceKeyId = deviceKeyId
                )
                val response = apiService.verifyMedia(
                    auth = "", // TODO: handle auth
                    request = request
                )
                Result.Success(response)
            } catch (e: Exception) {
                Log.e(TAG, "Verification failed", e)
                when (e) {
                    is ApiException -> Result.Error(
                        exception = e,
                        errorCode = e.errorCode,
                        message = e.message
                    )
                    else -> Result.Error(exception = e, message = e.message)
                }
            }
        }
    }
    /**
     * Verify a file by generating fingerprint and hash.
     */
    suspend fun verifyFile(
        file: File,
        mediaType: String,
        mode: String = "standard"
    ): Result<MultiAxisVerdict> {
        return withContext(Dispatchers.IO) {
            try {
                val sha256 = HashUtils.sha256(file)
                var canonicalHash: String? = null
                var fingerprint: RobustFingerprint? = null

                if (mode != "fast") {
                    // Generate fingerprint
                    fingerprint = when (mediaType) {
                        Constants.MEDIA_TYPE_IMAGE -> FingerprintUtils.generateImageFingerprint(file.absolutePath)
                        Constants.MEDIA_TYPE_VIDEO -> FingerprintUtils.generateVideoFingerprint(file.absolutePath)
                        else -> null
                    }
                    // Canonical hash (optional)
                    canonicalHash = HashUtils.canonicalHash(file, mediaType)
                }

                verifyMedia(
                    sha256 = sha256,
                    mediaType = mediaType,
                    fingerprintProfile = fingerprint,
                    mode = mode,
                    canonicalHash = canonicalHash
                )
            } catch (e: Exception) {
                Log.e(TAG, "File verification failed", e)
                Result.Error(exception = e, message = e.message)
            }
        }
    }

    // ============================
    // Search
    // ============================

    /**
     * Search for evidence.
     */
    suspend fun searchEvidence(
        sha256: String,
        mediaType: String,
        fingerprintProfile: RobustFingerprint? = null,
        canonicalHash: String? = null,
        maxResults: Int = 10,
        maxCandidates: Int = 100,
        filters: Map<String, String>? = null
    ): Result<SearchResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = SearchRequest(
                    sha256 = sha256,
                    canonicalMediaHash = canonicalHash,
                    robustFingerprintProfile = fingerprintProfile,
                    mediaType = mediaType,
                    filters = filters,
                    maxResults = maxResults,
                    maxCandidates = maxCandidates
                )
                val response = apiService.searchEvidence(
                    auth = "", // TODO: handle auth
                    request = request
                )
                Result.Success(response)
            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                when (e) {
                    is ApiException -> Result.Error(
                        exception = e,
                        errorCode = e.errorCode,
                        message = e.message
                    )
                    else -> Result.Error(exception = e, message = e.message)
                }
            }
        }
    }
    /**
     * Get related evidence.
     */
    suspend fun getRelatedEvidence(
        evidenceId: String,
        maxResults: Int = 10
    ): Result<RelatedEvidenceResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getRelatedEvidence(
                    evidenceId = evidenceId,
                    auth = "",
                    maxResults = maxResults
                )
                Result.Success(response)
            } catch (e: Exception) {
                Log.e(TAG, "Get related evidence failed", e)
                when (e) {
                    is ApiException -> Result.Error(
                        exception = e,
                        errorCode = e.errorCode,
                        message = e.message
                    )
                    else -> Result.Error(exception = e, message = e.message)
                }
            }
        }
    }

    // ============================
    // Device Lifecycle
    // ============================

    /**
     * Rotate device key.
     */
    suspend fun rotateDeviceKey(
        newDeviceKeyId: String,
        newPublicKey: String,
        attestationSummary: AttestationSummary? = null
    ): Result<DeviceKey> {
        return withContext(Dispatchers.IO) {
            try {
                val oldDeviceKeyId = getDeviceKeyId() ?: return@withContext Result.Error(
                    errorCode = "NO_DEVICE_KEY",
                    message = "No current device key found"
                )

                val request = DeviceRotationRequest(
                    newDeviceKeyId = newDeviceKeyId,
                    newPublicKey = newPublicKey,
                    attestationSummary = attestationSummary,
                    platform = "android"
                )

                val response = apiService.rotateDeviceKey(
                    deviceKeyId = oldDeviceKeyId,
                    auth = "",
                    request = request
                )

                // Update local cache
                saveDeviceKeyId(newDeviceKeyId)
                savePublicKey(newPublicKey)
                saveTrustTier(response.trustTier.name)

                Result.Success(response)
            } catch (e: Exception) {
                Log.e(TAG, "Device rotation failed", e)
                when (e) {
                    is ApiException -> Result.Error(
                        exception = e,
                        errorCode = e.errorCode,
                        message = e.message
                    )
                    else -> Result.Error(exception = e, message = e.message)
                }
            }
        }
    }
    /**
     * Revoke device key.
     */
    suspend fun revokeDeviceKey(deviceKeyId: String): Result<DeviceKey> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.revokeDeviceKey(
                    deviceKeyId = deviceKeyId,
                    auth = ""
                )
                // Clear local state if it's the current key
                if (deviceKeyId == getDeviceKeyId()) {
                    clearLocalDeviceState()
                }
                Result.Success(response)
            } catch (e: Exception) {
                Log.e(TAG, "Device revocation failed", e)
                when (e) {
                    is ApiException -> Result.Error(
                        exception = e,
                        errorCode = e.errorCode,
                        message = e.message
                    )
                    else -> Result.Error(exception = e, message = e.message)
                }
            }
        }
    }

    /**
     * Recover device lineage.
     */
    suspend fun recoverDeviceLineage(
        oldDeviceKeyId: String,
        newDeviceKeyId: String,
        newPublicKey: String,
        attestationSummary: AttestationSummary? = null,
        recoveryQuorum: String? = null
    ): Result<DeviceKey> {
        return withContext(Dispatchers.IO) {
            try {
                val request = DeviceRecoveryRequest(
                    oldDeviceKeyId = oldDeviceKeyId,
                    newDeviceKeyId = newDeviceKeyId,
                    newPublicKey = newPublicKey,
                    attestationSummary = attestationSummary,
                    platform = "android",
                    recoveryQuorum = recoveryQuorum
                )

                val response = apiService.recoverDeviceLineage(
                    auth = "",
                    request = request
                )

                // Update local cache to new key
                saveDeviceKeyId(newDeviceKeyId)
                savePublicKey(newPublicKey)
                saveTrustTier(response.trustTier.name)

                Result.Success(response)
            } catch (e: Exception) {
                Log.e(TAG, "Device recovery failed", e)
                when (e) {
                    is ApiException -> Result.Error(
                        exception = e,
                        errorCode = e.errorCode,
                        message = e.message
                    )
                    else -> Result.Error(exception = e, message = e.message)
                }
            }
        }
    }

    /**
     * Get device key info.
     */
    suspend fun getDeviceKeyInfo(deviceKeyId: String): Result<DeviceKey> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getDeviceKey(
                    deviceKeyId = deviceKeyId,
                    auth = ""
                )
                Result.Success(response)
            } catch (e: Exception) {
                Log.e(TAG, "Get device key failed", e)
                when (e) {
                    is ApiException -> Result.Error(
                        exception = e,
                        errorCode = e.errorCode,
                        message = e.message
                    )
                    else -> Result.Error(exception = e, message = e.message)
                }
            }
        }
    }

    /**
     * List device keys.
     */
    suspend fun listDeviceKeys(
        trustTier: String? = null,
        lifecycleState: String? = null,
        page: Int = 1,
        limit: Int = 20
    ): Result<DeviceListResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.listDeviceKeys(
                    auth = "",
                    trustTier = trustTier,
                    lifecycleState = lifecycleState,
                    page = page,
                    limit = limit
                )
                Result.Success(response)
            } catch (e: Exception) {
                Log.e(TAG, "List device keys failed", e)
                when (e) {
                    is ApiException -> Result.Error(
                        exception = e,
                        errorCode = e.errorCode,
                        message = e.message
                    )
                    else -> Result.Error(exception = e, message = e.message)
                }
            }
        }
    }
    private fun clearLocalDeviceState() {
        val prefs = context.getSharedPreferences("dmvp_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove(PrefsKeys.KEY_DEVICE_KEY_ID)
            .remove(PrefsKeys.KEY_PUBLIC_KEY)
            .remove(PrefsKeys.KEY_TRUST_TIER)
            .apply()
        RetrofitClient.clearSession()
    }

    // ============================
    // Ownership Claims
    // ============================

    /**
     * Submit ownership claim.
     */
    suspend fun submitOwnershipClaim(
        evidenceId: String,
        claimantIdentity: String,
        claimType: String,
        supportingData: Map<String, Any>? = null
    ): Result<OwnershipClaim> {
        return withContext(Dispatchers.IO) {
            try {
                val request = OwnershipClaimRequest(
                    evidenceId = evidenceId,
                    claimantIdentity = claimantIdentity,
                    claimType = claimType,
                    supportingData = supportingData
                )
                val response = apiService.submitOwnershipClaim(
                    auth = "",
                    request = request
                )
                Result.Success(response)
            } catch (e: Exception) {
                Log.e(TAG, "Ownership claim submission failed", e)
                when (e) {
                    is ApiException -> Result.Error(
                        exception = e,
                        errorCode = e.errorCode,
                        message = e.message
                    )
                    else -> Result.Error(exception = e, message = e.message)
                }
            }
        }
    }

    /**
     * Get ownership claims for evidence.
     */
    suspend fun getOwnershipClaims(evidenceId: String): Result<OwnershipClaimListResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getOwnershipClaims(
                    evidenceId = evidenceId,
                    auth = ""
                )
                Result.Success(response)
            } catch (e: Exception) {
                Log.e(TAG, "Get ownership claims failed", e)
                when (e) {
                    is ApiException -> Result.Error(
                        exception = e,
                        errorCode = e.errorCode,
                        message = e.message
                    )
                    else -> Result.Error(exception = e, message = e.message)
                }
            }
        }
    }

    /**
     * Review ownership claim (admin).
     */
    suspend fun reviewOwnershipClaim(
        evidenceId: String,
        claimId: String,
        reviewStatus: String,
        reviewNotes: String? = null
    ): Result<OwnershipClaim> {
        return withContext(Dispatchers.IO) {
            try {
                val request = OwnershipClaimReviewRequest(
                    reviewStatus = reviewStatus,
                    reviewNotes = reviewNotes
                )
                val response = apiService.reviewOwnershipClaim(
                    evidenceId = evidenceId,
                    claimId = claimId,
                    auth = "",
                    request = request
                )
                Result.Success(response)
            } catch (e: Exception) {
                Log.e(TAG, "Review ownership claim failed", e)
                when (e) {
                    is ApiException -> Result.Error(
                        exception = e,
                        errorCode = e.errorCode,
                        message = e.message
                    )
                    else -> Result.Error(exception = e, message = e.message)
                }
            }
        }
    }// ============================
    // Premium Backup (optional)
    // ============================

    suspend fun backupMedia(evidenceId: String, encryptedData: ByteArray): Result<BackupResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = BackupRequest(
                    evidenceId = evidenceId,
                    encryptedData = android.util.Base64.encodeToString(encryptedData, android.util.Base64.NO_WRAP)
                )
                val response = apiService.backupMedia(
                    auth = "",
                    request = request
                )
                Result.Success(response)
            } catch (e: Exception) {
                Log.e(TAG, "Backup media failed", e)
                when (e) {
                    is ApiException -> Result.Error(
                        exception = e,
                        errorCode = e.errorCode,
                        message = e.message
                    )
                    else -> Result.Error(exception = e, message = e.message)
                }
            }
        }
    }

    suspend fun restoreMedia(evidenceId: String): Result<RestoreResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val request = RestoreRequest(evidenceId)
                val response = apiService.restoreMedia(
                    auth = "",
                    request = request
                )
                Result.Success(response)
            } catch (e: Exception) {
                Log.e(TAG, "Restore media failed", e)
                when (e) {
                    is ApiException -> Result.Error(
                        exception = e,
                        errorCode = e.errorCode,
                        message = e.message
                    )
                    else -> Result.Error(exception = e, message = e.message)
                }
            }
        }
    }

    suspend fun getPremiumStatus(): Result<PremiumStatus> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getPremiumStatus(auth = "")
                Result.Success(response)
            } catch (e: Exception) {
                Log.e(TAG, "Get premium status failed", e)
                when (e) {
                    is ApiException -> Result.Error(
                        exception = e,
                        errorCode = e.errorCode,
                        message = e.message
                    )
                    else -> Result.Error(exception = e, message = e.message)
                }
            }
        }
    }

    // ============================
    // Policy
    // ============================

    suspend fun getVerificationPolicy(): Result<VerificationPolicy> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getVerificationPolicy(auth = "")
                Result.Success(response)
            } catch (e: Exception) {
                Log.e(TAG, "Get verification policy failed", e)
                when (e) {
                    is ApiException -> Result.Error(
                        exception = e,
                        errorCode = e.errorCode,
                        message = e.message
                    )
                    else -> Result.Error(exception = e, message = e.message)
                }
            }
        }
    }
}

// ============================
// Typealiases for convenience
// ============================

    typealias RepositoryResult<T> = Result<T>
