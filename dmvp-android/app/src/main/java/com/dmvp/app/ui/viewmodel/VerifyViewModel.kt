/**
 * app/src/main/java/com/dmvp/app/ui/viewmodel/VerifyViewModel.kt
 *
 * ViewModel for the Verify screen.
 */

package com.dmvp.app.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmvp.app.data.model.*
import com.dmvp.app.data.repository.DMVPRepository
import com.dmvp.app.data.repository.Result
import com.dmvp.app.security.FingerprintUtils
import com.dmvp.app.security.HashUtils
import com.dmvp.app.utils.DmvpConstants
import com.dmvp.app.utils.VerificationConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val TAG = "VerifyViewModel"

data class VerifyUiState(
    val isLoading: Boolean = false,
    val selectedFile: File? = null,
    val selectedUri: Uri? = null,
    val mediaType: String? = null,
    val sha256: String? = null,
    val canonicalHash: String? = null,
    val fingerprint: RobustFingerprint? = null,
    val verdict: MultiAxisVerdict? = null,
    val verificationMode: String = VerificationConstants.MODE_STANDARD,
    val progress: Float = 0f,
    val isVerified: Boolean = false,
    val error: String? = null,
    val errorCode: String? = null,
    val hasResult: Boolean = false,
    val warningMessages: List<String> = emptyList()
)

@HiltViewModel
class VerifyViewModel @Inject constructor(
    private val repository: DMVPRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerifyUiState())
    val uiState: StateFlow<VerifyUiState> = _uiState.asStateFlow()

    fun setVerificationMode(mode: String) {
        _uiState.update { it.copy(verificationMode = mode) }
    }

    fun setFile(file: File, mediaType: String) {
        _uiState.update {
            it.copy(
                selectedFile = file,
                selectedUri = null,
                mediaType = mediaType,
                hasResult = false,
                isVerified = false,
                verdict = null,
                error = null,
                errorCode = null
            )
        }
        processFile(file, mediaType)
    }

    fun setUri(uri: Uri, mediaType: String) {
        _uiState.update {
            it.copy(
                selectedUri = uri,
                selectedFile = null,
                mediaType = mediaType,
                hasResult = false,
                isVerified = false,
                verdict = null,
                error = null,
                errorCode = null
            )
        }
    }

    private fun processFile(file: File, mediaType: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, progress = 0.1f) }

                val sha256 = HashUtils.sha256(file)
                _uiState.update { it.copy(sha256 = sha256, progress = 0.4f) }

                val canonicalHash = HashUtils.canonicalHash(file, mediaType)
                _uiState.update { it.copy(canonicalHash = canonicalHash, progress = 0.6f) }

                val fingerprint = when (mediaType) {
                    DmvpConstants.MEDIA_TYPE_IMAGE -> FingerprintUtils.generateImageFingerprint(file.absolutePath)
                    DmvpConstants.MEDIA_TYPE_VIDEO -> FingerprintUtils.generateVideoFingerprint(file.absolutePath)
                    else -> null
                }
                _uiState.update {
                    it.copy(
                        fingerprint = fingerprint,
                        progress = 0.9f,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process file", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to process file",
                        errorCode = "PROCESSING_ERROR"
                    )
                }
            }
        }
    }

    fun verify() {
        val state = uiState.value
        val sha256 = state.sha256
        val mediaType = state.mediaType
        val fingerprint = state.fingerprint
        val canonicalHash = state.canonicalHash
        val mode = state.verificationMode

        if (sha256 == null || mediaType == null) {
            _uiState.update {
                it.copy(
                    error = "Missing required data. Please select a media file.",
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
                        progress = 0.1f,
                        error = null,
                        errorCode = null,
                        hasResult = false,
                        isVerified = false
                    )
                }

                val result = repository.verifyMedia(
                    sha256 = sha256,
                    mediaType = mediaType,
                    fingerprintProfile = fingerprint,
                    mode = mode,
                    canonicalHash = canonicalHash,
                    deviceKeyId = null
                )

                when (result) {
                    is Result.Success -> {
                        val verdict = result.data
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                verdict = verdict,
                                isVerified = true,
                                hasResult = true,
                                progress = 1f,
                                warningMessages = verdict.warnings,
                                error = null,
                                errorCode = null
                            )
                        }
                        Log.d(TAG, "Verification complete: integrity=${verdict.integrityVerdict}, provenance=${verdict.provenanceVerdict}, similarity=${verdict.similarityVerdict}")
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = result.message ?: "Verification failed",
                                errorCode = result.errorCode ?: "VERIFICATION_FAILED",
                                hasResult = false,
                                isVerified = false
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
                        hasResult = false,
                        isVerified = false
                    )
                }
            }
        }
    }

    fun reset() {
        _uiState.update {
            VerifyUiState(verificationMode = it.verificationMode)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, errorCode = null) }
    }

    fun updateProgress(progress: Float) {
        _uiState.update { it.copy(progress = progress) }
    }

    fun setFileDirect(file: File, mediaType: String) {
        setFile(file, mediaType)
    }
}

fun VerifyUiState.hasSuccessfulVerification(): Boolean = isVerified && verdict != null

fun VerifyUiState.getVerdictSummary(): String {
    val v = verdict ?: return "No verification result"
    return when {
        v.integrityVerdict == IntegrityVerdict.EXACT_MATCH &&
                v.provenanceVerdict == ProvenanceVerdict.SIGNED_TRUSTED_DEVICE &&
                v.evidenceQualityVerdict == EvidenceQualityVerdict.HIGH_EVIDENTIARY_STRENGTH ->
            "✅ Verified - Strong Evidence"
        v.integrityVerdict == IntegrityVerdict.EXACT_MATCH &&
                v.provenanceVerdict == ProvenanceVerdict.SIGNED_TRUSTED_DEVICE ->
            "✅ Verified - Moderate Evidence"
        v.integrityVerdict == IntegrityVerdict.EXACT_MATCH ->
            "📄 Integrity Verified, Provenance Uncertain"
        v.similarityVerdict == SimilarityVerdict.STRONG_DERIVATIVE &&
                v.provenanceVerdict == ProvenanceVerdict.SIGNED_TRUSTED_DEVICE ->
            "🔗 Similar Derivative Found (Trusted)"
        v.similarityVerdict == SimilarityVerdict.STRONG_DERIVATIVE ->
            "🔗 Similar Derivative Found (Provenance Uncertain)"
        v.similarityVerdict == SimilarityVerdict.PROBABLE_DERIVATIVE ->
            "🔍 Probable Derivative Found"
        else -> "❌ No Strong Evidence Found"
    }
}

fun VerifyUiState.getVerdictColor(): Int {
    val v = verdict ?: return 0xFFFF6D00.toInt()
    return when {
        v.integrityVerdict == IntegrityVerdict.EXACT_MATCH &&
                v.provenanceVerdict == ProvenanceVerdict.SIGNED_TRUSTED_DEVICE &&
                v.evidenceQualityVerdict == EvidenceQualityVerdict.HIGH_EVIDENTIARY_STRENGTH ->
            0xFF00E676.toInt()
        v.integrityVerdict == IntegrityVerdict.EXACT_MATCH ||
                v.similarityVerdict == SimilarityVerdict.STRONG_DERIVATIVE ->
            0xFFFFD740.toInt()
        else -> 0xFFE53935.toInt()
    }
}
