/**
 * app/src/main/java/com/dmvp/app/ui/viewmodel/SearchViewModel.kt
 *
 * ViewModel for the Search screen.
 */

package com.dmvp.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmvp.app.data.model.*
import com.dmvp.app.data.remote.SearchResponse
import com.dmvp.app.data.remote.RelatedEvidenceResponse
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

private const val TAG = "SearchViewModel"

enum class SearchMode {
    EXACT, SIMILARITY, METADATA
}

data class SearchUiState(
    val isLoading: Boolean = false,
    val searchMode: SearchMode = SearchMode.EXACT,
    val querySha256: String = "",
    val selectedFile: File? = null,
    val mediaType: String? = null,
    val fingerprint: RobustFingerprint? = null,
    val filters: Map<String, String> = emptyMap(),
    val maxResults: Int = 10,
    val maxCandidates: Int = 100,
    val searchResults: SearchResponse? = null,
    val relatedResults: RelatedEvidenceResponse? = null,
    val matchedEvidence: List<MatchedEvidence> = emptyList(),
    val totalMatches: Int = 0,
    val bestMatchType: String = "none",
    val bestScore: Double = 0.0,
    val progress: Float = 0f,
    val hasSearched: Boolean = false,
    val error: String? = null,
    val errorCode: String? = null,
    val selectedEvidenceId: String? = null,
    val expandedMatchIndex: Int? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: DMVPRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun setSearchMode(mode: SearchMode) {
        _uiState.update { it.copy(searchMode = mode, hasSearched = false, searchResults = null) }
    }

    fun setQuerySha256(hash: String) {
        _uiState.update {
            it.copy(
                querySha256 = hash,
                hasSearched = false,
                searchResults = null,
                error = null,
                errorCode = null
            )
        }
    }

    fun setSearchFile(file: File, mediaType: String) {
        _uiState.update {
            it.copy(
                selectedFile = file,
                mediaType = mediaType,
                hasSearched = false,
                searchResults = null,
                fingerprint = null,
                error = null,
                errorCode = null
            )
        }
        processFileForSearch(file, mediaType)
    }

    private fun processFileForSearch(file: File, mediaType: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, progress = 0.1f) }

                val sha256 = HashUtils.sha256(file)
                _uiState.update { it.copy(querySha256 = sha256, progress = 0.4f) }

                val fingerprint = when (mediaType) {
                    DmvpConstants.MEDIA_TYPE_IMAGE -> FingerprintUtils.generateImageFingerprint(file.absolutePath)
                    DmvpConstants.MEDIA_TYPE_VIDEO -> FingerprintUtils.generateVideoFingerprint(file.absolutePath)
                    else -> null
                }
                _uiState.update {
                    it.copy(
                        fingerprint = fingerprint,
                        progress = 0.8f,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process file for search", e)
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

    fun setFilters(filters: Map<String, String>) {
        _uiState.update { it.copy(filters = filters) }
    }

    fun setPagination(maxResults: Int, maxCandidates: Int) {
        _uiState.update {
            it.copy(
                maxResults = maxResults,
                maxCandidates = maxCandidates
            )
        }
    }

    fun performSearch() {
        val state = uiState.value
        val sha256 = state.querySha256
        val mediaType = state.mediaType
        val fingerprint = state.fingerprint
        val filters = state.filters
        val maxResults = state.maxResults
        val maxCandidates = state.maxCandidates

        if (sha256.isEmpty()) {
            _uiState.update {
                it.copy(
                    error = "Please enter a SHA-256 hash or select a file",
                    errorCode = "VALIDATION_ERROR"
                )
            }
            return
        }

        if (state.searchMode == SearchMode.SIMILARITY && fingerprint == null) {
            _uiState.update {
                it.copy(
                    error = "Fingerprint not available. Please select a media file.",
                    errorCode = "VALIDATION_ERROR"
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
                        hasSearched = false,
                        searchResults = null,
                        error = null,
                        errorCode = null,
                        matchedEvidence = emptyList()
                    )
                }

                val result = repository.searchEvidence(
                    sha256 = sha256,
                    mediaType = mediaType ?: DmvpConstants.MEDIA_TYPE_IMAGE,
                    fingerprintProfile = fingerprint,
                    canonicalHash = null,
                    maxResults = maxResults,
                    maxCandidates = maxCandidates,
                    filters = filters
                )

                when (result) {
                    is Result.Success -> {
                        val response = result.data
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                searchResults = response,
                                matchedEvidence = response.matchedEvidence ?: emptyList(),
                                totalMatches = response.totalMatches,
                                bestMatchType = response.bestMatchType ?: "none",
                                bestScore = response.bestScore,
                                progress = 1f,
                                hasSearched = true,
                                error = null,
                                errorCode = null,
                                expandedMatchIndex = null
                            )
                        }
                        Log.d(TAG, "Search complete: found ${response.totalMatches} matches")
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = result.message ?: "Search failed",
                                errorCode = result.errorCode ?: "SEARCH_FAILED",
                                hasSearched = true
                            )
                        }
                        Log.e(TAG, "Search failed: ${result.errorCode} - ${result.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search error", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Search error",
                        errorCode = "SEARCH_ERROR",
                        hasSearched = true
                    )
                }
            }
        }
    }

    fun searchByHash(sha256: String) {
        setQuerySha256(sha256)
        _uiState.update { it.copy(searchMode = SearchMode.EXACT) }
        performSearch()
    }

    fun getRelatedEvidence(evidenceId: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null, errorCode = null) }
                val result = repository.getRelatedEvidence(evidenceId, maxResults = 10)
                when (result) {
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                relatedResults = result.data,
                                selectedEvidenceId = evidenceId
                            )
                        }
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = result.message ?: "Failed to get related evidence",
                                errorCode = result.errorCode ?: "RELATED_ERROR"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get related evidence", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to get related evidence",
                        errorCode = "RELATED_ERROR"
                    )
                }
            }
        }
    }

    fun clearResults() {
        _uiState.update {
            it.copy(
                searchResults = null,
                matchedEvidence = emptyList(),
                hasSearched = false,
                selectedEvidenceId = null,
                relatedResults = null,
                error = null,
                errorCode = null,
                expandedMatchIndex = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, errorCode = null) }
    }

    fun toggleMatchExpanded(index: Int) {
        _uiState.update {
            it.copy(
                expandedMatchIndex = if (it.expandedMatchIndex == index) null else index
            )
        }
    }

    fun selectMatch(evidenceId: String) {
        _uiState.update { it.copy(selectedEvidenceId = evidenceId) }
    }

    fun reset() {
        _uiState.update {
            SearchUiState(
                searchMode = it.searchMode,
                maxResults = it.maxResults,
                maxCandidates = it.maxCandidates
            )
        }
    }
}

fun SearchUiState.hasResults(): Boolean = hasSearched && matchedEvidence.isNotEmpty()
fun SearchUiState.hasExactMatch(): Boolean = bestMatchType == "exact"
fun SearchUiState.getBestMatchId(): String? = matchedEvidence.firstOrNull()?.evidenceId

fun SearchUiState.getBestMatchDescription(): String {
    return when (bestMatchType) {
        "exact" -> "Exact match found (SHA-256 identical)"
        "canonical" -> "Canonical match found"
        "similarity" -> "Similarity match (score: ${String.format("%.2f", bestScore * 100)}%)"
        "none" -> "No matches found"
        else -> "Unknown match type"
    }
}

fun MatchedEvidence.getMatchTypeColor(): Int = when (matchType) {
    "exact" -> 0xFF00E676.toInt()
    "canonical" -> 0xFF00BCD4.toInt()
    "similarity" -> 0xFFFFD740.toInt()
    else -> 0xFFFF6D00.toInt()
}

fun MatchedEvidence.getMatchTypeLabel(): String = when (matchType) {
    "exact" -> "Exact Match"
    "canonical" -> "Canonical Match"
    "similarity" -> "Similarity Match"
    else -> "Match"
}

fun MatchedEvidence.getFormattedScore(): String {
    return if (similarityScore != null) {
        String.format("%.1f%%", similarityScore * 100)
    } else {
        "N/A"
    }
}
