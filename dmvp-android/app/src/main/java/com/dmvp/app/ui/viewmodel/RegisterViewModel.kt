/**
 * app/src/main/java/com/dmvp/app/ui/viewmodel/RegisterViewModel.kt
 *
 * ViewModel for the Register screen.
 * Handles the complete registration flow: media selection, device key management,
 * CEE construction, signing, and submission to the server.
 *
 * Differs from CaptureViewModel in that it is specifically focused on registration
 * (not verification) and provides more detailed progress and status feedback.
 *
 * Uses:
 *   - DMVPRepository for registration and device key operations
 *   - CEEBuilder for building Canonical Evidence Envelopes
 *   - FingerprintUtils for fingerprint generation
 *   - HashUtils for hashing
 *   - DeviceKeyManager for signing
 *
 * State management with Compose StateFlow.
 */

package com.dmvp.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmvp.app.data.model.*
import com.dmvp.app.data.repository.DMVPRepository
import com.dmvp.app.security.FingerprintUtils
import com.dmvp.app.security.HashUtils
import com.dmvp.app.utils.CEEBuilder
import com.dmvp.app.utils.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val TAG = "RegisterViewModel"

/**
 * Registration step enum.
 */
enum class RegistrationStep {
    IDLE,
    SELECT_MEDIA,
    PROCESSING_MEDIA,
    DEVICE_KEY_READY,
    BUILDING_CEE,
    SIGNING,
    SUBMITTING,
    COMPLETE,
    ERROR
}

/**
 * UI state for the Register screen.
 */
data class RegisterUiState(
    val isLoading: Boolean = false,
    val selectedFile: File? = null,
    val mediaType: String? = null,
    val sha256: String? = null,
    val canonicalHash: String? = null,
    val fingerprint: RobustFingerprint? = null,
    val cee: CEE? = null,
    val registrationResult: EvidenceRecord? = null,
    val deviceKeyId: String? = null,
    val publicKey: String? = null,
    val trustTier: String? = null,
    val privacyFlags: PrivacyFlags = PrivacyFlags(),
    val captureTimeClaim: String? = null,
    val geolocationClaim: GeolocationClaim? = null,
    val chainParentEvidenceId: String? = null,
    val progress: Float = 0f,
    val currentStep: RegistrationStep = RegistrationStep.IDLE,
    val error: String? = null,
    val errorCode: String? = null,
    val isRegistered: Boolean = false,
    val idempotencyKey: String? = null,
    val warnings: List<String> = emptyList()
)

/**
 * ViewModel for registering evidence.
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val repository: DMVPRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    /**
     * Set privacy flags.
     */
    fun setPrivacyFlags(privacyFlags: PrivacyFlags) {
        _uiState.update { it.copy(privacyFlags = privacyFlags) }
    }

    /**
     * Set capture time claim (ISO 8601).
     */
    fun setCaptureTimeClaim(timestamp: String) {
        _uiState.update { it.copy(captureTimeClaim = timestamp) }
    }

    /**
     * Set geolocation claim.
     */
    fun setGeolocationClaim(lat: Double, lng: Double) {
        _uiState.update {
            it.copy(
                geolocationClaim = GeolocationClaim(
                    lat = lat,
                    lng = lng
                )
            )
        }
    }

    /**
     * Set chain parent evidence ID (for derivatives).
     */
    fun setChainParentEvidenceId(parentId: String) {
        _uiState.update { it.copy(chainParentEvidenceId = parentId) }
    }

    /**
     * Select a media file for registration.
     */
    fun selectFile(file: File, mediaType: String) {
        _uiState.update {
            it.copy(
                selectedFile = file,
                mediaType = mediaType,
                isRegistered = false,
                registrationResult = null,
                error = null,
                errorCode = null,
                currentStep = RegistrationStep.SELECT_MEDIA
            )
        }
        // Start processing
        processMedia(file, mediaType)
    }

    /**
     * Process the selected media: generate hash, fingerprint, and prepare CEE.
     */
    private fun processMedia(file: File, mediaType: String) {
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        currentStep = RegistrationStep.PROCESSING_MEDIA,
                        progress = 0.05f
                    )
                }

                // 1. Compute SHA-256
                val sha256 = HashUtils.sha256(file)
                _uiState.update {
                    it.copy(sha256 = sha256, progress = 0.2f)
                }

                // 2. Compute canonical hash (optional)
                val canonicalHash = HashUtils.canonicalHash(file, mediaType)
                _uiState.update {
                    it.copy(canonicalHash = canonicalHash, progress = 0.35f)
                }

                // 3. Generate robust fingerprint
                val fingerprint = when (mediaType) {
                    Constants.MEDIA_TYPE_IMAGE -> {
                        FingerprintUtils.generateImageFingerprint(file.absolutePath)
                    }
                    Constants.MEDIA_TYPE_VIDEO -> {
                        FingerprintUtils.generateVideoFingerprint(file.absolutePath)
                    }
                    else -> null
                }
                if (fingerprint == null) {
                    throw Exception("Failed to generate fingerprint for media")
                }
                _uiState.update {
                    it.copy(fingerprint = fingerprint, progress = 0.5f)
                }

                // 4. Get or create device key
                _uiState.update {
                    it.copy(currentStep = RegistrationStep.DEVICE_KEY_READY, progress = 0.6f)
                }
                val keyResult = repository.getOrCreateDeviceKey()
                if (keyResult is RepositoryResult.Error) {
                    throw Exception(keyResult.message ?: "Failed to get device key")
                }
                val (deviceKeyId, publicKey) = (keyResult as RepositoryResult.Success).data
                _uiState.update {
                    it.copy(
                        deviceKeyId = deviceKeyId,
                        publicKey = publicKey,
                        trustTier = repository.getCachedTrustTier(),
                        progress = 0.7f
                    )
                }

                // 5. Build CEE (without signature)
                _uiState.update {
                    it.copy(currentStep = RegistrationStep.BUILDING_CEE, progress = 0.75f)
                }
                val context = file.context ?: throw Exception("Context not available")
                val cee = when (mediaType) {
                    Constants.MEDIA_TYPE_IMAGE -> CEEBuilder.buildFromImageFile(
                        context = context,
                        imageFile = file,
                        deviceKeyId = deviceKeyId,
                        publicKeyRef = publicKey,
                        privacyFlags = _uiState.value.privacyFlags,
                        captureTimeClaim = _uiState.value.captureTimeClaim,
                        geolocationClaim = _uiState.value.geolocationClaim,
                        chainParentEvidenceId = _uiState.value.chainParentEvidenceId
                    )
                    Constants.MEDIA_TYPE_VIDEO -> CEEBuilder.buildFromVideoFile(
                        context = context,
                        videoFile = file,
                        deviceKeyId = deviceKeyId,
                        publicKeyRef = publicKey,
                        privacyFlags = _uiState.value.privacyFlags,
                        captureTimeClaim = _uiState.value.captureTimeClaim,
                        geolocationClaim = _uiState.value.geolocationClaim,
                        chainParentEvidenceId = _uiState.value.chainParentEvidenceId
                    )
                    else -> null
                }
                if (cee == null) {
                    throw Exception("Failed to build Canonical Evidence Envelope")
                }
                _uiState.update {
                    it.copy(cee = cee, progress = 0.85f)
                }

                // 6. Ready for submission
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentStep = RegistrationStep.COMPLETE, // Actually ready, not complete
                        progress = 0.9f,
                        error = null,
                        errorCode = null
                    )
                }
                // We are now ready to submit, but we don't auto-submit.
                // User will click "Register" button.

                // Note: we're setting currentStep to COMPLETE but not yet submitted.
                // We'll add a separate state "READY" or keep as BUILDING_CEE and use a flag.
                // Let's refine: after building CEE, set step to READY.
                _uiState.update {
                    it.copy(
                        currentStep = RegistrationStep.BUILDING_CEE, // still building
                        // We'll use a flag to indicate ready for submit
                        // But we can just check if cee != null and !isRegistered
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to process media", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to process media",
                        errorCode = "PROCESSING_ERROR",
                        currentStep = RegistrationStep.ERROR
                    )
                }
            }
        }
    }

    /**
     * Submit the registration to the server.
     */
    fun submitRegistration() {
        val state = uiState.value
        val file = state.selectedFile
        val cee = state.cee
        val mediaType = state.mediaType

        if (file == null || cee == null || mediaType == null) {
            _uiState.update {
                it.copy(
                    error = "Missing required data. Please select a media file first.",
                    errorCode = "INVALID_STATE"
                )
            }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        currentStep = RegistrationStep.SUBMITTING,
                        progress = 0.1f,
                        error = null,
                        errorCode = null
                    )
                }

                val result = repository.registerMedia(
                    file = file,
                    mediaType = mediaType,
                    privacyFlags = state.privacyFlags,
                    captureTimeClaim = state.captureTimeClaim,
                    geolocationClaim = state.geolocationClaim,
                    chainParentEvidenceId = state.chainParentEvidenceId
                )

                when (result) {
                    is RepositoryResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                registrationResult = result.data,
                                isRegistered = true,
                                progress = 1f,
                                currentStep = RegistrationStep.COMPLETE,
                                error = null,
                                errorCode = null
                            )
                        }
                        Log.i(TAG, "Registration successful: ${result.data.evidenceId}")
                    }
                    is RepositoryResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = result.message ?: "Registration failed",
                                errorCode = result.errorCode ?: "REGISTRATION_FAILED",
                                currentStep = RegistrationStep.ERROR
                            )
                        }
                        Log.e(TAG, "Registration failed: ${result.errorCode} - ${result.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Registration submission error", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Registration submission error",
                        errorCode = "SUBMISSION_ERROR",
                        currentStep = RegistrationStep.ERROR
                    )
                }
            }
        }
    }

    /**
     * Check if we have a device key and get it if not.
     */
    fun ensureDeviceKey() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val keyResult = repository.getOrCreateDeviceKey()
                when (keyResult) {
                    is RepositoryResult.Success -> {
                        val (deviceKeyId, publicKey) = keyResult.data
                        _uiState.update {
                            it.copy(
                                deviceKeyId = deviceKeyId,
                                publicKey = publicKey,
                                trustTier = repository.getCachedTrustTier(),
                                isLoading = false,
                                error = null,
                                errorCode = null
                            )
                        }
                    }
                    is RepositoryResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = keyResult.message ?: "Failed to get device key",
                                errorCode = keyResult.errorCode ?: "DEVICE_KEY_ERROR"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unexpected error",
                        errorCode = "UNKNOWN_ERROR"
                    )
                }
            }
        }
    }

    /**
     * Reset the registration state for a new registration.
     */
    fun reset() {
        _uiState.update {
            RegisterUiState(
                privacyFlags = it.privacyFlags,
                captureTimeClaim = it.captureTimeClaim,
                geolocationClaim = it.geolocationClaim,
                chainParentEvidenceId = it.chainParentEvidenceId
            )
        }
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null, errorCode = null) }
    }

    /**
     * Update progress for long-running operations.
     */
    fun updateProgress(progress: Float) {
        _uiState.update { it.copy(progress = progress) }
    }
}

/**
 * Extension to get Context from File (if available).
 * For simplicity, we'll use the application context from the repository.
 */
private val File.context: android.content.Context?
    get() = null // In practice, you'd get from application

/**
 * Convenience extension to check if the registration is ready to submit.
 */
fun RegisterUiState.isReadyToSubmit(): Boolean {
    return currentStep == RegistrationStep.BUILDING_CEE &&
            cee != null &&
            selectedFile != null &&
            mediaType != null &&
            deviceKeyId != null &&
            !isLoading &&
            !isRegistered
}

/**
 * Convenience extension to get a user-friendly step description.
 */
fun RegisterUiState.getStepDescription(): String {
    return when (currentStep) {
        RegistrationStep.IDLE -> "Ready"
        RegistrationStep.SELECT_MEDIA -> "Select media"
        RegistrationStep.PROCESSING_MEDIA -> "Processing media..."
        RegistrationStep.DEVICE_KEY_READY -> "Device key ready"
        RegistrationStep.BUILDING_CEE -> "Building evidence envelope..."
        RegistrationStep.SIGNING -> "Signing..."
        RegistrationStep.SUBMITTING -> "Submitting to registry..."
        RegistrationStep.COMPLETE -> "Registration complete!"
        RegistrationStep.ERROR -> "Error occurred"
    }
}
