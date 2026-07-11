/**
 * app/src/main/java/com/dmvp/app/ui/viewmodel/CaptureViewModel.kt
 *
 * ViewModel for the Capture screen.
 * Handles media capture (camera/gallery), fingerprint generation,
 * and preparation for registration or verification.
 */

package com.dmvp.app.ui.viewmodel

import android.content.Context
import android.net.Uri
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
import com.dmvp.app.utils.VerificationConstants
import com.dmvp.app.utils.currentIso8601
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val TAG = "CaptureViewModel"

data class CaptureUiState(
    val isLoading: Boolean = false,
    val isCapturing: Boolean = false,
    val selectedFile: File? = null,
    val selectedUri: Uri? = null,
    val mediaType: String? = null,
    val sha256: String? = null,
    val canonicalHash: String? = null,
    val fingerprint: RobustFingerprint? = null,
    val cee: CEE? = null,
    val registrationResult: EvidenceRecord? = null,
    val verificationResult: MultiAxisVerdict? = null,
    val error: String? = null,
    val errorCode: String? = null,
    val isRegistered: Boolean = false,
    val isVerified: Boolean = false,
    val privacyFlags: PrivacyFlags = PrivacyFlags(),
    val captureTimeClaim: String? = null,
    val geolocationClaim: GeolocationClaim? = null,
    val verificationMode: String = VerificationConstants.MODE_STANDARD,
    val progress: Float = 0f,
    val validationMode: ValidationMode = ValidationMode.IDLE
)

enum class ValidationMode {
    IDLE, READY_FOR_REGISTRATION, READY_FOR_VERIFICATION, PROCESSING, COMPLETE, ERROR
}

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val repository: DMVPRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun setPrivacyFlags(privacyFlags: PrivacyFlags) {
        _uiState.update { it.copy(privacyFlags = privacyFlags) }
    }

    fun setVerificationMode(mode: String) {
        _uiState.update { it.copy(verificationMode = mode) }
    }

    fun setCaptureTimeClaim(timestamp: String) {
        _uiState.update { it.copy(captureTimeClaim = timestamp) }
    }

    fun setGeolocationClaim(lat: Double, lng: Double) {
        _uiState.update {
            it.copy(geolocationClaim = GeolocationClaim(lat = lat, lng = lng))
        }
    }

    fun setCapturedFile(file: File, mediaType: String) {
        _uiState.update {
            it.copy(selectedFile = file, mediaType = mediaType, isCapturing = false, validationMode = ValidationMode.PROCESSING)
        }
        processMediaFile(file, mediaType)
    }

    fun setGalleryFile(file: File, mediaType: String) {
        _uiState.update {
            it.copy(selectedFile = file, selectedUri = null, mediaType = mediaType, isCapturing = false, validationMode = ValidationMode.PROCESSING)
        }
        processMediaFile(file, mediaType)
    }

    fun setGalleryUri(uri: Uri, mediaType: String) {
        _uiState.update {
            it.copy(selectedUri = uri, selectedFile = null, mediaType = mediaType, isCapturing = false, validationMode = ValidationMode.PROCESSING)
        }
    }

    private fun processMediaFile(file: File, mediaType: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, progress = 0.1f) }

                val sha256 = HashUtils.sha256(file)
                _uiState.update { it.copy(sha256 = sha256, progress = 0.3f) }

                val canonicalHash = HashUtils.canonicalHash(file, mediaType)
                _uiState.update { it.copy(canonicalHash = canonicalHash, progress = 0.5f) }

                val fingerprint = when (mediaType) {
                    DmvpConstants.MEDIA_TYPE_IMAGE -> FingerprintUtils.generateImageFingerprint(file.absolutePath)
                    DmvpConstants.MEDIA_TYPE_VIDEO -> FingerprintUtils.generateVideoFingerprint(file.absolutePath, maxKeyframes = 10)
                    else -> null
                }
                _uiState.update { it.copy(fingerprint = fingerprint, progress = 0.7f) }

                val deviceKeyResult = repository.getOrCreateDeviceKey()
                if (deviceKeyResult is Result.Success) {
                    val (deviceKeyId, publicKey) = deviceKeyResult.data
                    val cee = when (mediaType) {
                        DmvpConstants.MEDIA_TYPE_IMAGE -> CEEBuilder.buildFromImageFile(
                            context = file.context ?: return@launch,
                            imageFile = file,
                            deviceKeyId = deviceKeyId,
                            publicKeyRef = publicKey,
                            privacyFlags = uiState.value.privacyFlags,
                            captureTimeClaim = uiState.value.captureTimeClaim,
                            geolocationClaim = uiState.value.geolocationClaim
                        )
                        DmvpConstants.MEDIA_TYPE_VIDEO -> CEEBuilder.buildFromVideoFile(
                            context = file.context ?: return@launch,
                            videoFile = file,
                            deviceKeyId = deviceKeyId,
                            publicKeyRef = publicKey,
                            privacyFlags = uiState.value.privacyFlags,
                            captureTimeClaim = uiState.value.captureTimeClaim,
                            geolocationClaim = uiState.value.geolocationClaim
                        )
                        else -> null
                    }
                    _uiState.update {
                        it.copy(
                            cee = cee,
                            progress = 0.9f,
                            isLoading = false,
                            validationMode = ValidationMode.READY_FOR_REGISTRATION
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to get device key",
                            errorCode = "DEVICE_KEY_ERROR",
                            validationMode = ValidationMode.ERROR
                        )
                    }
                }

                _uiState.update { it.copy(progress = 1f) }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to process media file", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to process media",
                        errorCode = "PROCESSING_ERROR",
                        validationMode = ValidationMode.ERROR
                    )
                }
            }
        }
    }

    fun registerEvidence() {
        val state = uiState.value
        val file = state.selectedFile
        val cee = state.cee
        if (file == null || cee == null) {
            _uiState.update {
                it.copy(error = "No media selected or CEE not built", errorCode = "INVALID_STATE")
            }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, validationMode = ValidationMode.PROCESSING) }
                val result = repository.registerMedia(
                    file = file,
                    mediaType = state.mediaType ?: DmvpConstants.MEDIA_TYPE_IMAGE,
                    privacyFlags = state.privacyFlags,
                    captureTimeClaim = state.captureTimeClaim,
                    geolocationClaim = state.geolocationClaim
                )
                when (result) {
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                registrationResult = result.data,
                                isRegistered = true,
                                validationMode = ValidationMode.COMPLETE,
                                error = null,
                                errorCode = null
                            )
                        }
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = result.message ?: "Registration failed",
                                errorCode = result.errorCode ?: "REGISTRATION_FAILED",
                                validationMode = ValidationMode.ERROR
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Registration failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Registration failed",
                        errorCode = "REGISTRATION_ERROR",
                        validationMode = ValidationMode.ERROR
                    )
                }
            }
        }
    }

    fun verifyEvidence() {
        val state = uiState.value
        val sha256 = state.sha256
        val mediaType = state.mediaType
        if (sha256 == null || mediaType == null) {
            _uiState.update {
                it.copy(error = "Missing required data for verification", errorCode = "INVALID_STATE")
            }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, validationMode = ValidationMode.PROCESSING) }
                val result = repository.verifyMedia(
                    sha256 = sha256,
                    mediaType = mediaType,
                    fingerprintProfile = state.fingerprint,
                    mode = state.verificationMode,
                    canonicalHash = state.canonicalHash,
                    deviceKeyId = null
                )
                when (result) {
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                verificationResult = result.data,
                                isVerified = true,
                                validationMode = ValidationMode.COMPLETE,
                                error = null,
                                errorCode = null
                            )
                        }
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = result.message ?: "Verification failed",
                                errorCode = result.errorCode ?: "VERIFICATION_FAILED",
                                validationMode = ValidationMode.ERROR
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Verification failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Verification failed",
                        errorCode = "VERIFICATION_ERROR",
                        validationMode = ValidationMode.ERROR
                    )
                }
            }
        }
    }

    fun resetCapture() {
        _uiState.update {
            CaptureUiState(
                privacyFlags = it.privacyFlags,
                captureTimeClaim = it.captureTimeClaim,
                geolocationClaim = it.geolocationClaim,
                verificationMode = it.verificationMode
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, errorCode = null) }
    }

    fun updateProgress(progress: Float) {
        _uiState.update { it.copy(progress = progress) }
    }

    fun setCapturing(isCapturing: Boolean) {
        _uiState.update { it.copy(isCapturing = isCapturing) }
    }
}

private val File.context: Context? get() = null

fun CaptureUiState.isReadyForRegistration(): Boolean {
    return validationMode == ValidationMode.READY_FOR_REGISTRATION &&
            selectedFile != null && cee != null && !isLoading
}

fun CaptureUiState.isReadyForVerification(): Boolean {
    return (validationMode == ValidationMode.READY_FOR_REGISTRATION || validationMode == ValidationMode.COMPLETE) &&
            sha256 != null && mediaType != null && !isLoading
}

fun CaptureUiState.getMediaSummary(): String {
    val file = selectedFile
    val type = mediaType ?: "unknown"
    val size = file?.length() ?: 0L
    val sizeStr = when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
        else -> String.format("%.1f MB", size / (1024.0 * 1024.0))
    }
    return "$type file, $sizeStr"
}
