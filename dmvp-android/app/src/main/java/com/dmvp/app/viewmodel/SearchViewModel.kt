/**
 * Phase 3 Step 3.5: Search ViewModel
 * Handles cross-modal search logic
 */

package com.dmvp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmvp.app.data.model.SearchResult
import com.dmvp.app.data.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun searchByText() {
        if (_searchQuery.value.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val results = searchRepository.searchByText(
                    query = _searchQuery.value,
                    vectorType = "siglip",
                    limit = 20,
                    threshold = 0.7f
                )
                _searchResults.value = results
            } catch (e: Exception) {
                _errorMessage.value = "Search failed: ${e.message}"
                _searchResults.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun captureImageForSearch() {
        // In production, this would open camera or gallery picker
        // For now, placeholder
        _errorMessage.value = "Image search not yet implemented in UI"
    }

    fun searchByImage(imageData: ByteArray) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val results = searchRepository.searchByImage(
                    imageData = imageData,
                    vectorType = "siglip",
                    limit = 20,
                    threshold = 0.7f
                )
                _searchResults.value = results
            } catch (e: Exception) {
                _errorMessage.value = "Image search failed: ${e.message}"
                _searchResults.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearResults() {
        _searchResults.value = emptyList()
        _errorMessage.value = null
    }
}
