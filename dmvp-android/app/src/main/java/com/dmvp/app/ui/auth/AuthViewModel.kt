package com.dmvp.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmvp.app.data.model.AuthModels
import com.dmvp.app.data.remote.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val requiresOTP: Boolean = false,
    val loginSuccess: Boolean = false,
    val user: AuthModels.UserResponse? = null,
    val token: String? = null
)

class AuthViewModel : ViewModel() {
    
    private val apiService = RetrofitClient.getService()
    
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    
    fun signUp(email: String, password: String, name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val request = AuthModels.SignUpRequest(
                    email = email,
                    password = password,
                    name = name
                )
                
                val response = apiService.signUp(request)
                
                if (response.success) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = "Sign up successful! Please check your email to verify your account.",
                        user = response.user,
                        token = response.token
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.error ?: "Sign up failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Network error"
                )
            }
        }
    }
    
    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val request = AuthModels.SignInRequest(
                    email = email,
                    password = password
                )
                
                val response = apiService.signIn(request)
                
                if (response.success) {
                    if (response.requiresOTP == true) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            requiresOTP = true,
                            message = response.message
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            loginSuccess = true,
                            user = response.user,
                            token = response.token
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.error ?: "Sign in failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Network error"
                )
            }
        }
    }
    
    fun verifyOTP(email: String, otp: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val request = AuthModels.VerifyOTPRequest(
                    email = email,
                    otp = otp
                )
                
                val response = apiService.verifyOTP(request)
                
                if (response.success) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        loginSuccess = true,
                        user = response.user,
                        token = response.token,
                        message = "OTP verified successfully!"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.error ?: "OTP verification failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Network error"
                )
            }
        }
    }
    
    fun resendOTP(email: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val request = AuthModels.ResendOTPRequest(email = email)
                val response = apiService.resendOTP(request)
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = if (response.success) "OTP sent successfully!" else response.error
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Network error"
                )
            }
        }
    }
    
    fun signInWithGoogle() {
        // TODO: Implement Google Sign-In
        // This requires Google Sign-In SDK integration
        _uiState.value = _uiState.value.copy(
            error = "Google Sign-In not yet implemented"
        )
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
