/**
 * app/src/main/java/com/dmvp/app/ui/viewmodel/VerifyViewModel.kt
 *
 * ViewModel for the Verify screen.
 * Handles media selection, verification against the registry,
 * and displaying the multi-axis verdict results.
 *
 * Supports three verification modes: fast, standard, deep.
 * Shows detailed verdict components (integrity, provenance, similarity, evidence quality).
 *
 * Uses:
 *   - DMVPRepository for verification operations
 *   - FingerprintUtils for generating fingerprints from selected media
 *   - HashUtils for computing hashes
 */

package com.dmvp.app.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmvp.app.data.model.*
import com.dmvp.app.data.repository.DMVPRepository
import com.dmvp.app.security.FingerprintUtils
import com.dmvp.app.security.HashUtils
import com.dmvp.app.utils.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val TAG = "VerifyViewModel"

/**
 * UI state for the Verify screen.
 */
data class VerifyUiState(
    val isLoading: Boolean = false,
    val selectedFile: File? = null,
    val selectedUri: Uri? = null,
    val mediaType: String? = null,
    val sha256: String? = null,
    val canonicalHash: String? = null,
    val fingerprint: RobustFingerprint? = null,
    val verdict: MultiAxisVerdict? = null,
    val verificationMode: String = Constants.MODE_STANDARD,
    val progress: Float = 0f,
    val isVerified: Boolean = false,
    val error: String? = null,
    val errorCode: String? = null,
    val hasResult: Boolean = false,
    val warningMessages: List<String> = emptyList()
)

/**
 * ViewModel for verifying media files.
 */
@HiltViewModel
class VerifyViewModel @Inject constructor(
    private val repository: DMVPRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerifyUiState())
    val uiState: StateFlow<VerifyUiState> = _uiState.asStateFlow()

    /**
     * Set verification mode.
     */
    fun setVerificationMode(mode: String) {
        _uiState.update { it.copy(verificationMode = mode) }
    }

    /**
     * Set selected media file from gallery.
     */
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
        // Automatically process the file
        processFile(file, mediaType)
    }

    /**
     * Set selected media from URI.
     */
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
        // URI processing is handled in the fragment/activity
        // The fragment should convert URI to File and call setFile()
    }

    /**
     * Process the selected file: generate hash and fingerprint.
     */
    private fun processFile(file: File, mediaType: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, progress = 0.1f) }

                // Compute SHA-256
                val sha256 = HashUtils.sha256(file)
                _uiState.update { it.copy(sha256 = sha256, progress = 0.4f) }

                // Compute canonical hash (optional)
                val canonicalHash = HashUtils.canonicalHash(file, mediaType)
                _uiState.update { it.copy(canonicalHash = canonicalHash, progress = 0.6f) }

                // Generate fingerprint (for similarity checks)
                val fingerprint = when (mediaType) {
                    Constants.MEDIA_TYPE_IMAGE -> {
                        FingerprintUtils.generateImageFingerprint(file.absolutePath)
                    }
                    Constants.MEDIA_TYPE_VIDEO -> {
                        FingerprintUtils.generateVideoFingerprint(file.absolutePath)
                    }
                    else -> null
                }
                _uiState.update {
                    it.copy(
                        fingerprint = fingerprint,
                        progress = 0.9f,
                        isLoading = false
                    )
                }

                // Auto-verify if we have all data? Or wait for user action.
                // We'll wait for explicit verify action.

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

    /**
     * Verify the selected media against the registry.
     */
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
                    deviceKeyId = null // server will determine
                )

                when (result) {
                    is RepositoryResult.Success -> {
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
                        // Log verification result
                        Log.d(TAG, "Verification complete: integrity=${verdict.integrityVerdict}, " +
                                "provenance=${verdict.provenanceVerdict}, " +
                                "similarity=${verdict.similarityVerdict}")
                    }
                    is RepositoryResult.Error -> {
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

    /**
     * Reset the verification state for a new file.
     */
    fun reset() {
        _uiState.update {
            VerifyUiState(
                verificationMode = it.verificationMode // keep mode preference
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

    /**
     * Set file directly (for use when URI is converted to File).
     */
    fun setFileDirect(file: File, mediaType: String) {
        setFile(file, mediaType)
    }
}

// ================================
// Extension functions for UI state
// ================================

/**
 * Check if the UI state has a successful verification result.
 */
fun VerifyUiState.hasSuccessfulVerification(): Boolean {
    return isVerified && verdict != null
}

/**
 * Get a user-friendly summary of the verification result.
 */
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

/**
 * Get color resource for the overall verdict (to be used with theme colors).
 */
fun VerifyUiState.getVerdictColor(): Int {
    val v = verdict ?: return 0xFFFF6D00.toInt() // orange for unknown
    return when {
        v.integrityVerdict == IntegrityVerdict.EXACT_MATCH &&
                v.provenanceVerdict == ProvenanceVerdict.SIGNED_TRUSTED_DEVICE &&
                v.evidenceQualityVerdict == EvidenceQualityVerdict.HIGH_EVIDENTIARY_STRENGTH ->
            0xFF00E676.toInt() // green

        v.integrityVerdict == IntegrityVerdict.EXACT_MATCH ||
                v.similarityVerdict == SimilarityVerdict.STRONG_DERIVATIVE ->
            0xFFFFD740.toInt() // amber

        else -> 0xFFE53935.toInt() // red
    }
}
