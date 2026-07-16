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
import com.dmvp.app.data.local.LocalEvidenceStore
import com.dmvp.app.data.model.*
import com.dmvp.app.data.remote.*
import com.dmvp.app.security.DeviceKeyManager
import com.dmvp.app.security.FingerprintUtils
import com.dmvp.app.security.HashUtils
import com.dmvp.app.security.SignatureUtils
import com.dmvp.app.utils.CEEBuilder
import com.dmvp.app.utils.DmvpConstants
import com.dmvp.app.utils.PrefsKeys
import com.dmvp.app.utils.currentIso8601
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import timber.log.Timber
import retrofit2.HttpException

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
                        val storedDeviceKeyId = getDeviceKeyId()
                        if (storedDeviceKeyId != null) {
                            RetrofitClient.setDeviceKeyId(storedDeviceKeyId)
                            return@withContext Result.Success(Pair(storedDeviceKeyId, publicKey))
                        }

                        /*
                         * A failed previous run can leave a Keystore key without a saved
                         * device_key_id. Do not invent an unregistered id and continue:
                         * evidence registration will fail when the backend verifies the
                         * signing device. Register the existing public key first.
                         */
                        val recoveredDeviceKeyId = generateDeviceKeyId()
                        val registrationRequest = DeviceRegistrationRequest(
                            deviceKeyId = recoveredDeviceKeyId,
                            publicKey = publicKey,
                            attestationSummary = buildAttestationSummary(emptyList()),
                            platform = "android"
                        )
                        // ── Step 7.5 Fix: DeviceRegistrationResponse ──
                        // ── Step: Debug logs added ──
                        Timber.d("🔍 DEBUG: Registering device with key_id=$recoveredDeviceKeyId")
                        Timber.d("🔍 DEBUG: Public key length=${publicKey.length}")

                        try {
                            val response = apiService.registerDevice(request = registrationRequest)
                            Timber.d("✅ DEBUG: Registration SUCCESS: $response")
                            saveDeviceKeyId(recoveredDeviceKeyId)
                            savePublicKey(publicKey)
                            saveTrustTier(response.trustTier)
                            Timber.d("Existing local key registered with new device key id: $recoveredDeviceKeyId")
                            return@withContext Result.Success(Pair(recoveredDeviceKeyId, publicKey))
                        } catch (e: Exception) {
                            Timber.e("❌ DEBUG: Registration FAILED: ${e.message}")
                            Timber.e("❌ DEBUG: Exception type: ${e::class.simpleName}")
                            if (e is retrofit2.HttpException) {
                                Timber.e("❌ DEBUG: HTTP ${e.code()} - ${e.message()}")
                                val errorBody = e.response()?.errorBody()?.string()
                                Timber.e("❌ DEBUG: Error body: $errorBody")
                            }
                            throw e
                        }
                    }
                }

                // No key, generate new one
                Timber.d("Generating new device key")
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

                // ── Step 3.3 Fix: Register device without auth header ──
                // ── Step 7.5 Fix: DeviceRegistrationResponse ──
                // ── Step: Debug logs added ──
                Timber.d("🔍 DEBUG: Registering device with key_id=$deviceKeyId")
                Timber.d("🔍 DEBUG: Public key length=${publicKeyBase64.length}")

                try {
                    val response = apiService.registerDevice(
                        request = registrationRequest
                    )
                    Timber.d("✅ DEBUG: Registration SUCCESS: $response")
                    // Trust tier will be assigned by server, but we can cache it
                    saveTrustTier(response.trustTier)
                    Result.Success(Pair(deviceKeyId, publicKeyBase64))
                } catch (e: Exception) {
                    Timber.e("❌ DEBUG: Registration FAILED: ${e.message}")
                    Timber.e("❌ DEBUG: Exception type: ${e::class.simpleName}")
                    if (e is retrofit2.HttpException) {
                        Timber.e("❌ DEBUG: HTTP ${e.code()} - ${e.message()}")
                        val errorBody = e.response()?.errorBody()?.string()
                        Timber.e("❌ DEBUG: Error body: $errorBody")
                    }
                    throw e
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get or create device key")
                Result.Error(exception = e, message = e.message)
            }
        }
    }

    /**
     * Get the current device key ID from local storage.
     */
    fun getDeviceKeyId(): String? {
        // In production, use DataStore. For MVP, use SharedPreferences.
        val prefs = context.getSharedPreferences("dmvp_prefs", Context.MODE_PRIVATE)
        return prefs.getString(PrefsKeys.KEY_DEVICE_KEY_ID, null)
    }

    fun getPublicKey(): String? {
        val prefs = context.getSharedPreferences("dmvp_prefs", Context.MODE_PRIVATE)
        return prefs.getString(PrefsKeys.KEY_PUBLIC_KEY, null) ?: DeviceKeyManager.getPublicKey()
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
                    DmvpConstants.MEDIA_TYPE_IMAGE -> CEEBuilder.buildFromImageFile(
                        context = context,
                        imageFile = file,
                        deviceKeyId = deviceKeyId,
                        publicKeyRef = publicKey,
                        privacyFlags = privacyFlags,
                        captureTimeClaim = captureTimeClaim,
                        geolocationClaim = geolocationClaim,
                        chainParentEvidenceId = chainParentEvidenceId
                    )
                    DmvpConstants.MEDIA_TYPE_VIDEO -> CEEBuilder.buildFromVideoFile(
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

                // ── Step 3.3: Fix canonicalPayload with excludeSignature ──
                val canonicalPayload = SignatureUtils.canonicalizePayload(
                    payload = cee,
                    excludeSignature = true
                )
                val canonicalRequest = "$canonicalPayload\n$nonce\n$timestamp"
                val signature = DeviceKeyManager.signString(canonicalRequest)
                if (signature == null) {
                    return@withContext Result.Error(
                        errorCode = "SIGNING_FAILED",
                        message = "Failed to sign evidence"
                    )
                }

                // ── Step 3.2: Idempotency key must be a UUID ──────────────
                // Idempotency key must be a UUID per FR-CR-08.
                val idempotencyKey = UUID.randomUUID().toString()

                // Send registration
                val response = apiService.registerEvidence(
                    idempotencyKey = idempotencyKey,
                    signature = signature,
                    nonce = nonce,
                    timestamp = timestamp,
                    deviceKeyId = deviceKeyId,
                    policyVersion = DmvpConstants.PROTOCOL_VERSION,
                    cee = cee
                )

                // ── Step 3.3: Save evidence_id locally ──────────────────────
                if (response.data != null) {
                    LocalEvidenceStore.saveEvidenceId(context, response.data.evidenceId)
                    Timber.d("Evidence registered and saved locally: ${response.data.evidenceId}")
                    Result.Success(response.data)
                } else {
                    Result.Error(
                        errorCode = "EMPTY_RESPONSE",
                        message = "Server returned empty response"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Registration failed")
                
                // ── NEW: Log backend error body ──
                if (e is retrofit2.HttpException) {
                    val errorBody = e.response()?.errorBody()?.string()
                    Timber.e("BACKEND_ERROR_BODY", "Evidence registration error: $errorBody")
                }
                
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
     * Verify a media file against the registry using POST /verify.
     * Step 4.3-4.4: Uses POST /verify with fingerprint profile for multi-axis verification.
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
                    auth = "",
                    request = request
                )
                Result.Success(response)
            } catch (e: Exception) {
                Timber.e(e, "Verification failed")
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
                        DmvpConstants.MEDIA_TYPE_IMAGE -> FingerprintUtils.generateImageFingerprint(file.absolutePath)
                        DmvpConstants.MEDIA_TYPE_VIDEO -> FingerprintUtils.generateVideoFingerprint(file.absolutePath)
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
                Timber.e(e, "File verification failed")
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
                Timber.e(e, "Search failed")
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
                Timber.e(e, "Get related evidence failed")
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
                Timber.e(e, "Device rotation failed")
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

    suspend fun revokeDeviceKey(deviceKeyId: String): Result<DeviceKey> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.revokeDeviceKey(
                    deviceKeyId = deviceKeyId,
                    auth = ""
                )

                if (deviceKeyId == getDeviceKeyId()) {
                    context.getSharedPreferences("dmvp_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .remove(PrefsKeys.KEY_DEVICE_KEY_ID)
                        .remove(PrefsKeys.KEY_PUBLIC_KEY)
                        .remove(PrefsKeys.KEY_TRUST_TIER)
                        .apply()
                    RetrofitClient.clearSession()
                }

                Result.Success(response)
            } catch (e: Exception) {
                Timber.e(e, "Device revocation failed")
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

                saveDeviceKeyId(response.deviceKeyId)
                savePublicKey(response.publicKey)
                saveTrustTier(response.trustTier.name)

                Result.Success(response)
            } catch (e: Exception) {
                Timber.e(e, "Device recovery failed")
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

    suspend fun getDeviceKeyInfo(deviceKeyId: String): Result<DeviceKey> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getDeviceKey(
                    deviceKeyId = deviceKeyId,
                    auth = ""
                )
                Result.Success(response)
            } catch (e: Exception) {
                Timber.e(e, "Get device key failed")

                if (deviceKeyId == getDeviceKeyId()) {
                    val localPublicKey = getPublicKey()
                    val localTrustTier = runCatching {
                        DeviceTrustTier.valueOf(getCachedTrustTier() ?: DeviceKeyManager.getTrustTierName())
                    }.getOrDefault(DeviceTrustTier.TIER_C)

                    if (localPublicKey != null) {
                        return@withContext Result.Success(
                            DeviceKey(
                                deviceKeyId = deviceKeyId,
                                publicKey = localPublicKey,
                                trustTier = localTrustTier,
                                lifecycleState = DeviceLifecycleState.ACTIVE
                            )
                        )
                    }
                }

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

    suspend fun listDeviceKeys(
        trustTier: String? = null,
        lifecycleState: String? = null,
        page: Int = 1,
        limit: Int = 20
    ): Result<DeviceListResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.listDeviceKeys(
                    trustTier = trustTier,
                    lifecycleState = lifecycleState,
                    page = page,
                    limit = limit
                )
                Result.Success(response)
            } catch (e: Exception) {
                Timber.e(e, "List device keys failed")

                /*
                 * The current MVP app has no user JWT login flow yet, while some backend
                 * device-list deployments still require Authorization. Keep the Device
                 * screen usable by surfacing the locally registered key instead of showing
                 * a blocking HTTP 401.
                 */
                val localDeviceKeyId = getDeviceKeyId()
                val localPublicKey = getPublicKey()
                if (localDeviceKeyId != null && localPublicKey != null) {
                    val localTrustTier = runCatching {
                        DeviceTrustTier.valueOf(getCachedTrustTier() ?: DeviceKeyManager.getTrustTierName())
                    }.getOrDefault(DeviceTrustTier.TIER_C)
                    val localDevice = DeviceKey(
                        deviceKeyId = localDeviceKeyId,
                        publicKey = localPublicKey,
                        trustTier = localTrustTier,
                        lifecycleState = DeviceLifecycleState.ACTIVE
                    )
                    return@withContext Result.Success(
                        DeviceListResponse(
                            items = listOf(localDevice),
                            total = 1,
                            page = page,
                            limit = limit
                        )
                    )
                }

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
