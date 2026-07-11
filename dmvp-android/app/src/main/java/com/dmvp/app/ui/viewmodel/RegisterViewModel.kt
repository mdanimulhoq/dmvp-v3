/**
 * app/src/main/java/com/dmvp/app/ui/viewmodel/RegisterViewModel.kt
 *
 * ViewModel for the Register screen.
 */

package com.dmvp.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmvp.app.data.model.*
import com.dmvp.app.data.remote.EvidenceRecord
import com.dmvp.app.data.repository.DMVPRepository
import com.dmvp.app.data.repository.Result
import com.dmvp.app.security.FingerprintUtils
import com.dmvp.app.security.HashUtils
import com.dmvp.app.utils.CEEBuilder
import com.dmvp.app.utils.DmvpConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val TAG = "RegisterViewModel"

enum class RegistrationStep {
    IDLE, SELECT_MEDIA, PROCESSING_MEDIA, DEVICE_KEY_READY, BUILDING_CEE, SIGNING, SUBMITTING, COMPLETE, ERROR
}

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

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val repository: DMVPRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun setPrivacyFlags(privacyFlags: PrivacyFlags) {
        _uiState.update { it.copy(privacyFlags = privacyFlags) }
    }

    fun setCaptureTimeClaim(timestamp: String) {
        _uiState.update { it.copy(captureTimeClaim = timestamp) }
    }

    fun setGeolocationClaim(lat: Double, lng: Double) {
        _uiState.update {
            it.copy(geolocationClaim = GeolocationClaim(lat = lat, lng = lng))
        }
    }

    fun setChainParentEvidenceId(parentId: String) {
        _uiState.update { it.copy(chainParentEvidenceId = parentId) }
    }

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
        processMedia(file, mediaType)
    }

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

                val sha256 = HashUtils.sha256(file)
                _uiState.update { it.copy(sha256 = sha256, progress = 0.2f) }

                val canonicalHash = HashUtils.canonicalHash(file, mediaType)
                _uiState.update { it.copy(canonicalHash = canonicalHash, progress = 0.35f) }

                val fingerprint = when (mediaType) {
                    DmvpConstants.MEDIA_TYPE_IMAGE -> FingerprintUtils.generateImageFingerprint(file.absolutePath)
                    DmvpConstants.MEDIA_TYPE_VIDEO -> FingerprintUtils.generateVideoFingerprint(file.absolutePath)
                    else -> null
                }
                if (fingerprint == null) {
                    throw Exception("Failed to generate fingerprint for media")
                }
                _uiState.update { it.copy(fingerprint = fingerprint, progress = 0.5f) }

                _uiState.update {
                    it.copy(currentStep = RegistrationStep.DEVICE_KEY_READY, progress = 0.6f)
                }
                val keyResult = repository.getOrCreateDeviceKey()
                if (keyResult is Result.Error) {
                    throw Exception(keyResult.message ?: "Failed to get device key")
                }
                val (deviceKeyId, publicKey) = (keyResult as Result.Success).data
                _uiState.update {
                    it.copy(
                        deviceKeyId = deviceKeyId,
                        publicKey = publicKey,
                        trustTier = repository.getCachedTrustTier(),
                        progress = 0.7f
                    )
                }

                _uiState.update {
                    it.copy(currentStep = RegistrationStep.BUILDING_CEE, progress = 0.75f)
                }
                val context = file.context ?: throw Exception("Context not available")
                val cee = when (mediaType) {
                    DmvpConstants.MEDIA_TYPE_IMAGE -> CEEBuilder.buildFromImageFile(
                        context = context,
                        imageFile = file,
                        deviceKeyId = deviceKeyId,
                        publicKeyRef = publicKey,
                        privacyFlags = _uiState.value.privacyFlags,
                        captureTimeClaim = _uiState.value.captureTimeClaim,
                        geolocationClaim = _uiState.value.geolocationClaim,
                        chainParentEvidenceId = _uiState.value.chainParentEvidenceId
                    )
                    DmvpConstants.MEDIA_TYPE_VIDEO -> CEEBuilder.buildFromVideoFile(
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
                    it.copy(
                        cee = cee,
                        progress = 0.85f,
                        isLoading = false,
                        currentStep = RegistrationStep.COMPLETE,
                        error = null,
                        errorCode = null
                    )
                }
                // Ready to submit, but we don't auto-submit.
                _uiState.update {
                    it.copy(currentStep = RegistrationStep.BUILDING_CEE) // Keep BUILDING_CEE until submit
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
                    is Result.Success -> {
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
                    is Result.Error -> {
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

    fun ensureDeviceKey() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val keyResult = repository.getOrCreateDeviceKey()
                when (keyResult) {
                    is Result.Success -> {
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
                    is Result.Error -> {
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

    fun clearError() {
        _uiState.update { it.copy(error = null, errorCode = null) }
    }

    fun updateProgress(progress: Float) {
        _uiState.update { it.copy(progress = progress) }
    }
}

private val File.context: android.content.Context? get() = null

fun RegisterUiState.isReadyToSubmit(): Boolean {
    return currentStep == RegistrationStep.BUILDING_CEE &&
            cee != null &&
            selectedFile != null &&
            mediaType != null &&
            deviceKeyId != null &&
            !isLoading &&
            !isRegistered
}

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
