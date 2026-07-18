/**
 * app/src/main/java/com/dmvp/app/ui/viewmodel/EvidenceDetailViewModel.kt
 *
 * Loads a single EvidenceRecord (registered media metadata) so it can be
 * shown after a Verify / Compare / Search result.
 */

package com.dmvp.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmvp.app.data.remote.EvidenceRecord
import com.dmvp.app.data.repository.DMVPRepository
import com.dmvp.app.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EvidenceDetailUiState(
    val isLoading: Boolean = false,
    val evidenceId: String? = null,
    val record: EvidenceRecord? = null,
    val error: String? = null,
    val errorCode: String? = null
)

@HiltViewModel
class EvidenceDetailViewModel @Inject constructor(
    private val repository: DMVPRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EvidenceDetailUiState())
    val uiState: StateFlow<EvidenceDetailUiState> = _uiState.asStateFlow()

    fun load(evidenceId: String) {
        if (evidenceId.isBlank()) {
            _uiState.update {
                it.copy(
                    error = "Missing evidence id",
                    errorCode = "INVALID_ARG"
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                isLoading = true,
                evidenceId = evidenceId,
                error = null,
                errorCode = null
            )
        }
        viewModelScope.launch {
            when (val res = repository.getEvidenceById(evidenceId)) {
                is Result.Success -> _uiState.update {
                    it.copy(isLoading = false, record = res.data)
                }
                is Result.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = res.message ?: "Failed to load evidence",
                        errorCode = res.errorCode
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, errorCode = null) }
    }
}
