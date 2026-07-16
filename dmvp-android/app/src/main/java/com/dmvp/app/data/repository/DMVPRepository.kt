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
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            android.util.Log.e("DMVP_DEBUG", "Evidence Registration Failed! HTTP Code: ${e.code()}")
            android.util.Log.e("DMVP_DEBUG", "Backend Error Body: $errorBody")
            
            // ── Step 7.5 Fix: Correct Result.Error argument order ──
            Result.Error(exception = e, errorCode = e.code().toString(), message = e.message())
        } catch (e: Exception) {
            android.util.Log.e("DMVP_DEBUG", "Unknown Error: ${e.message}")
            // ── Step 7.5 Fix: Correct Result.Error argument order ──
            Result.Error(exception = e, message = e.message ?: "Unknown error")
        }
    }
}
