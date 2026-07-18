/**
 * app/src/main/java/com/dmvp/app/ui/viewmodel/CompareViewModel.kt
 *
 * ViewModel for the Compare screen (DMVP core flow):
 *
 *   Step 1: user picks a REFERENCE media (a photo/video they previously
 *           registered on this or another device).
 *   Step 2: user picks a CANDIDATE media (the file they want to check).
 *   Step 3: app compares:
 *              - byte-for-byte via SHA-256 (exact match)
 *              - canonical hash equality (re-encoded / stripped metadata)
 *              - robust fingerprint similarity via /api/v1/verify against
 *                the reference's registered evidence record.
 *   Step 4: if the candidate matches, the reference's REGISTERED evidence
 *           record (device info, signer key id, timestamps, trust) is
 *           returned so the user can see the media's provenance.
 *
 * Backend endpoints used:
 *   - GET  /api/v1/evidence/by-hash/{sha256}   (lookup reference)
 *   - POST /api/v1/verify                      (multi-axis compare)
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
import com.dmvp.app.utils.DmvpConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val TAG = "CompareViewModel"

enum class CompareStep { PICK_REFERENCE, PICK_CANDIDATE, RESULT }

enum class CompareOutcome {
    /** SHA-256 bytes identical. Strongest match. */
    EXACT_MATCH,

    /** Canonical (re-encoded / metadata-stripped) hashes match. */
    CANONICAL_MATCH,

    /** Robust perceptual fingerprint matched above threshold. */
    SIMILAR_MATCH,

    /** Reference exists in registry but candidate does not match it. */
    NO_MATCH,

    /** Reference is not registered — cannot compare against provenance. */
    REFERENCE_NOT_REGISTERED
}

data class CompareUiState(
    val step: CompareStep = CompareStep.PICK_REFERENCE,
    val isLoading: Boolean = false,
    val progress: Float = 0f,

    val referenceFile: File? = null,
    val referenceMediaType: String? = null,
    val referenceSha256: String? = null,
    val referenceCanonicalHash: String? = null,
    val referenceRecord: EvidenceRecord? = null,

    val candidateFile: File? = null,
    val candidateMediaType: String? = null,
    val candidateSha256: String? = null,
    val candidateCanonicalHash: String? = null,

    val outcome: CompareOutcome? = null,
    val similarityScore: Double? = null,
    val verdict: MultiAxisVerdict? = null,

    val error: String? = null,
    val errorCode: String? = null
)

@HiltViewModel
class CompareViewModel @Inject constructor(
    private val repository: DMVPRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

    /**
     * Step 1 — user picked a reference (registered) media.
     * We compute its sha256 and look it up in the registry so we can show
     * the registered metadata later.
     */
    fun setReference(file: File, mediaType: String) {
        _uiState.update {
            it.copy(
                referenceFile = file,
                referenceMediaType = mediaType,
                referenceSha256 = null,
                referenceCanonicalHash = null,
                referenceRecord = null,
                candidateFile = null,
                candidateMediaType = null,
                candidateSha256 = null,
                candidateCanonicalHash = null,
                outcome = null,
                similarityScore = null,
                verdict = null,
                error = null,
                errorCode = null,
                isLoading = true,
                progress = 0.1f,
                step = CompareStep.PICK_REFERENCE
            )
        }
        viewModelScope.launch {
            try {
                val sha = HashUtils.sha256(file)
                val canonical = runCatching {
                    HashUtils.canonicalHash(file, mediaType)
                }.getOrNull()

                _uiState.update {
                    it.copy(
                        referenceSha256 = sha,
                        referenceCanonicalHash = canonical,
                        progress = 0.5f
                    )
                }

                when (val lookup = repository.getEvidenceByHash(sha)) {
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(
                                referenceRecord = lookup.data,
                                isLoading = false,
                                progress = 1f,
                                step = CompareStep.PICK_CANDIDATE
                            )
                        }
                    }
                    is Result.Error -> {
                        // Reference not registered — we can still let the
                        // user compare bytes/hashes locally, but we cannot
                        // surface any provenance metadata.
                        Log.i(TAG, "Reference not registered: ${lookup.message}")
                        _uiState.update {
                            it.copy(
                                referenceRecord = null,
                                isLoading = false,
                                progress = 1f,
                                step = CompareStep.PICK_CANDIDATE,
                                outcome = CompareOutcome.REFERENCE_NOT_REGISTERED,
                                error = "Reference not found in registry. " +
                                        "Register it first to get provenance metadata.",
                                errorCode = lookup.errorCode
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "setReference failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to read reference",
                        errorCode = "REFERENCE_ERROR"
                    )
                }
            }
        }
    }

    /**
     * Step 2 — user picked a candidate. Immediately runs compare.
     */
    fun setCandidate(file: File, mediaType: String) {
        val ref = _uiState.value.referenceFile
        val refType = _uiState.value.referenceMediaType
        val refSha = _uiState.value.referenceSha256
        if (ref == null || refType == null || refSha == null) {
            _uiState.update {
                it.copy(
                    error = "Please pick a reference media first.",
                    errorCode = "NO_REFERENCE"
                )
            }
            return
        }
        if (mediaType != refType) {
            _uiState.update {
                it.copy(
                    error = "Reference is $refType but candidate is $mediaType. " +
                            "Please pick the same media type.",
                    errorCode = "MEDIA_TYPE_MISMATCH"
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                candidateFile = file,
                candidateMediaType = mediaType,
                candidateSha256 = null,
                candidateCanonicalHash = null,
                outcome = null,
                similarityScore = null,
                verdict = null,
                error = null,
                errorCode = null,
                isLoading = true,
                progress = 0.1f
            )
        }
        compare()
    }

    private fun compare() {
        val state = _uiState.value
        val refFile = state.referenceFile ?: return
        val refType = state.referenceMediaType ?: return
        val refSha = state.referenceSha256 ?: return
        val refCanonical = state.referenceCanonicalHash
        val candFile = state.candidateFile ?: return
        val candType = state.candidateMediaType ?: return

        viewModelScope.launch {
            try {
                val candSha = HashUtils.sha256(candFile)
                _uiState.update {
                    it.copy(
                        candidateSha256 = candSha,
                        progress = 0.3f
                    )
                }

                // 1) exact byte match
                if (candSha.equals(refSha, ignoreCase = true)) {
                    _uiState.update {
                        it.copy(
                            outcome = CompareOutcome.EXACT_MATCH,
                            similarityScore = 1.0,
                            isLoading = false,
                            progress = 1f,
                            step = CompareStep.RESULT
                        )
                    }
                    return@launch
                }

                // 2) canonical hash match
                val candCanonical = runCatching {
                    HashUtils.canonicalHash(candFile, candType)
                }.getOrNull()
                _uiState.update {
                    it.copy(
                        candidateCanonicalHash = candCanonical,
                        progress = 0.5f
                    )
                }
                if (candCanonical != null && refCanonical != null &&
                    candCanonical.equals(refCanonical, ignoreCase = true)
                ) {
                    _uiState.update {
                        it.copy(
                            outcome = CompareOutcome.CANONICAL_MATCH,
                            similarityScore = 1.0,
                            isLoading = false,
                            progress = 1f,
                            step = CompareStep.RESULT
                        )
                    }
                    return@launch
                }

                // 3) similarity via /verify — send the candidate to the
                //    registry and see whether it matches the reference's
                //    fingerprint (or any near-duplicate registered record).
                val candFingerprint = when (candType) {
                    DmvpConstants.MEDIA_TYPE_IMAGE ->
                        FingerprintUtils.generateImageFingerprint(candFile.absolutePath)
                    DmvpConstants.MEDIA_TYPE_VIDEO ->
                        FingerprintUtils.generateVideoFingerprint(candFile.absolutePath)
                    else -> null
                }
                _uiState.update { it.copy(progress = 0.75f) }

                val verifyResult = repository.verifyMedia(
                    sha256 = candSha,
                    mediaType = candType,
                    fingerprintProfile = candFingerprint,
                    mode = "standard",
                    canonicalHash = candCanonical,
                    deviceKeyId = null
                )

                when (verifyResult) {
                    is Result.Success -> {
                        val v = verifyResult.data
                        // ── Step 11: Only use refMatch — no fallback to other registered evidence ──
                        val refMatch = v.matchedEvidenceList.firstOrNull {
                            it.evidenceId == state.referenceRecord?.evidenceId
                        }
                        val score = refMatch?.similarityScore
                        val outcome = when {
                            v.integrityVerdict == IntegrityVerdict.EXACT_MATCH ->
                                CompareOutcome.EXACT_MATCH
                            v.integrityVerdict == IntegrityVerdict.CANONICAL_MATCH ->
                                CompareOutcome.CANONICAL_MATCH
                            // Only SIMILAR_MATCH if the matched evidence IS our reference
                            refMatch != null && (
                                v.similarityVerdict == SimilarityVerdict.STRONG_DERIVATIVE ||
                                v.similarityVerdict == SimilarityVerdict.PROBABLE_DERIVATIVE
                            ) -> CompareOutcome.SIMILAR_MATCH
                            else -> CompareOutcome.NO_MATCH
                        }

                        _uiState.update {
                            it.copy(
                                verdict = v,
                                outcome = outcome,
                                similarityScore = score,
                                isLoading = false,
                                progress = 1f,
                                step = CompareStep.RESULT
                            )
                        }
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                outcome = CompareOutcome.NO_MATCH,
                                isLoading = false,
                                progress = 1f,
                                step = CompareStep.RESULT,
                                error = verifyResult.message
                                    ?: "Verification call failed",
                                errorCode = verifyResult.errorCode
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "compare failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Compare failed",
                        errorCode = "COMPARE_ERROR"
                    )
                }
            }
        }
    }

    fun clearReference() {
        _uiState.update { CompareUiState() }
    }

    fun clearCandidate() {
        _uiState.update {
            it.copy(
                candidateFile = null,
                candidateMediaType = null,
                candidateSha256 = null,
                candidateCanonicalHash = null,
                outcome = null,
                similarityScore = null,
                verdict = null,
                error = null,
                errorCode = null,
                step = CompareStep.PICK_CANDIDATE
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, errorCode = null) }
    }
}
