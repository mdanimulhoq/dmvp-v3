/**
 * app/src/main/java/com/dmvp/app/ui/viewmodel/SearchViewModel.kt
 *
 * ViewModel for the Search screen.
 * Handles searching for evidence by SHA-256 hash, fingerprint, or metadata.
 * Implements the staged matching pipeline (exact lookup, coarse candidate generation,
 * re-ranking, verdict construction) at the UI level via the repository.
 *
 * Supports:
 *   - Exact hash lookup
 *   - Similarity search using robust fingerprint profiles
 *   - Filtering by media type, device key, trust tier, etc.
 *   - Display of matched evidence with scores and match types
 *   - Navigation to evidence details
 *
 * Uses:
 *   - DMVPRepository for search operations
 *   - FingerprintUtils for generating fingerprints from selected media
 *   - HashUtils for hashing
 */

package com.dmvp.app.ui.viewmodel

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

private const val TAG = "SearchViewModel"

/**
 * Search mode enum.
 */
enum class SearchMode {
    EXACT,          // Search by SHA-256 hash only
    SIMILARITY,     // Search by file with fingerprint
    METADATA        // Search by metadata fields (not implemented in MVP)
}

/**
 * UI state for the Search screen.
 */
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

/**
 * ViewModel for searching evidence.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: DMVPRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /**
     * Set search mode.
     */
    fun setSearchMode(mode: SearchMode) {
        _uiState.update { it.copy(searchMode = mode, hasSearched = false, searchResults = null) }
    }

    /**
     * Set query SHA-256 hash.
     */
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

    /**
     * Set media file for similarity search.
     */
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
        // Process file to generate fingerprint
        processFileForSearch(file, mediaType)
    }

    /**
     * Process file to generate hash and fingerprint for search.
     */
    private fun processFileForSearch(file: File, mediaType: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, progress = 0.1f) }

                // Compute SHA-256 (for exact lookup)
                val sha256 = HashUtils.sha256(file)
                _uiState.update { it.copy(querySha256 = sha256, progress = 0.4f) }

                // Generate fingerprint (for similarity)
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

    /**
     * Set filters for search (e.g., device key, trust tier).
     */
    fun setFilters(filters: Map<String, String>) {
        _uiState.update { it.copy(filters = filters) }
    }

    /**
     * Set max results and max candidates.
     */
    fun setPagination(maxResults: Int, maxCandidates: Int) {
        _uiState.update {
            it.copy(
                maxResults = maxResults,
                maxCandidates = maxCandidates
            )
        }
    }

    /**
     * Execute the search based on current state.
     */
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

        // For similarity search, we need a fingerprint
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
                    mediaType = mediaType ?: Constants.MEDIA_TYPE_IMAGE,
                    fingerprintProfile = fingerprint,
                    canonicalHash = null, // Not used in MVP
                    maxResults = maxResults,
                    maxCandidates = maxCandidates,
                    filters = filters
                )

                when (result) {
                    is RepositoryResult.Success -> {
                        val response = result.data
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                searchResults = response,
                                matchedEvidence = response.matchedEvidence,
                                totalMatches = response.totalMatches,
                                bestMatchType = response.bestMatchType,
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
                    is RepositoryResult.Error -> {
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

    /**
     * Search by hash only (exact lookup).
     */
    fun searchByHash(sha256: String) {
        setQuerySha256(sha256)
        _uiState.update { it.copy(searchMode = SearchMode.EXACT) }
        performSearch()
    }

    /**
     * Get related evidence for a specific evidence ID.
     */
    fun getRelatedEvidence(evidenceId: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null, errorCode = null) }

                val result = repository.getRelatedEvidence(evidenceId, maxResults = 10)
                when (result) {
                    is RepositoryResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                relatedResults = result.data,
                                selectedEvidenceId = evidenceId
                            )
                        }
                    }
                    is RepositoryResult.Error -> {
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

    /**
     * Clear search results.
     */
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

    /**
     * Clear error state.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null, errorCode = null) }
    }

    /**
     * Toggle expanded state for a match item.
     */
    fun toggleMatchExpanded(index: Int) {
        _uiState.update {
            it.copy(
                expandedMatchIndex = if (it.expandedMatchIndex == index) null else index
            )
        }
    }

    /**
     * Select a match to view details.
     */
    fun selectMatch(evidenceId: String) {
        _uiState.update { it.copy(selectedEvidenceId = evidenceId) }
        // Could navigate to evidence detail screen via navigation
    }

    /**
     * Reset the search state to initial.
     */
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

// ================================
// Extension functions for UI state
// ================================

/**
 * Check if the search has any results.
 */
fun SearchUiState.hasResults(): Boolean {
    return hasSearched && matchedEvidence.isNotEmpty()
}

/**
 * Check if the search found an exact match.
 */
fun SearchUiState.hasExactMatch(): Boolean {
    return bestMatchType == "exact"
}

/**
 * Get the best match evidence ID (if any).
 */
fun SearchUiState.getBestMatchId(): String? {
    return matchedEvidence.firstOrNull()?.evidenceId
}

/**
 * Get a user-friendly description of the best match.
 */
fun SearchUiState.getBestMatchDescription(): String {
    return when (bestMatchType) {
        "exact" -> "Exact match found (SHA-256 identical)"
        "canonical" -> "Canonical match found"
        "similarity" -> "Similarity match (score: ${String.format("%.2f", bestScore * 100)}%)"
        "none" -> "No matches found"
        else -> "Unknown match type"
    }
}

/**
 * Get color for match type (for UI feedback).
 */
fun MatchedEvidence.getMatchTypeColor(): Int {
    return when (matchType) {
        "exact" -> 0xFF00E676.toInt() // green
        "canonical" -> 0xFF00BCD4.toInt() // cyan
        "similarity" -> 0xFFFFD740.toInt() // amber
        else -> 0xFFFF6D00.toInt() // orange
    }
}

/**
 * Get a label for match type.
 */
fun MatchedEvidence.getMatchTypeLabel(): String {
    return when (matchType) {
        "exact" -> "Exact Match"
        "canonical" -> "Canonical Match"
        "similarity" -> "Similarity Match"
        else -> "Match"
    }
}

/**
 * Format similarity score as percentage.
 */
fun MatchedEvidence.getFormattedScore(): String {
    return if (similarityScore != null) {
        String.format("%.1f%%", similarityScore * 100)
    } else {
        "N/A"
    }
}
