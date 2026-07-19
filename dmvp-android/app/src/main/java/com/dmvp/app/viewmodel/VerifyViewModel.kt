/**
 * Phase 3 Step 3.8: Verify ViewModel
 * Handles 10-layer verification logic
 */

package com.dmvp.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmvp.app.data.model.VerificationVerdict
import com.dmvp.app.data.repository.VerifyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VerifyViewModel @Inject constructor(
    private val verifyRepository: VerifyRepository
) : ViewModel() {

    private val _verdict = MutableStateFlow<VerificationVerdict?>(null)
    val verdict: StateFlow<VerificationVerdict?> = _verdict

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    /**
     * Verify an image asset
     */
    fun verifyImage(imageData: ByteArray) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _verdict.value = null

            try {
                val result = verifyRepository.verifyAsset(imageData)
                _verdict.value = result
            } catch (e: Exception) {
                _errorMessage.value = "Verification failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Verify by evidence ID
     */
    fun verifyByEvidenceId(evidenceId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _verdict.value = null

            try {
                val result = verifyRepository.verifyByEvidenceId(evidenceId)
                _verdict.value = result
            } catch (e: Exception) {
                _errorMessage.value = "Verification failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Clear current verdict
     */
    fun clearVerdict() {
        _verdict.value = null
        _errorMessage.value = null
    }
}
