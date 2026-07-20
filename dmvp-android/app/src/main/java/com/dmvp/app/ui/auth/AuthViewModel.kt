/**
 * app/src/main/java/com/dmvp/app/ui/auth/AuthViewModel.kt
 *
 * UDOVP V2 — Auth ViewModel
 * Connects to backend auth API: signup, signin, verify-otp, verify-email,
 * resend-otp, resend-verification, google
 *
 * PR 2: Auth Flow Screens
 */

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
    val signupSuccess: Boolean = false,
    val user: AuthModels.UserResponse? = null,
    val token: String? = null,
    val refreshToken: String? = null,
)

class AuthViewModel : ViewModel() {

    private val apiService = RetrofitClient.getService()

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // ═══════════════════════════════════════════════════════
    // Sign Up
    // ═══════════════════════════════════════════════════════

    fun signUp(email: String, password: String, name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val request = AuthModels.SignUpRequest(
                    email = email,
                    password = password,
                    name = name,
                )
                val response = apiService.signUp(request)

                if (response.success) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        signupSuccess = true,
                        message = "Account created! Check your email to verify.",
                        user = response.user,
                        token = response.token,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.error ?: "Sign up failed",
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Network error",
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // Sign In
    // ═══════════════════════════════════════════════════════

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val request = AuthModels.SignInRequest(
                    email = email,
                    password = password,
                )
                val response = apiService.signIn(request)

                if (response.success) {
                    if (response.requiresOTP == true) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            requiresOTP = true,
                            message = response.message,
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            loginSuccess = true,
                            user = response.user,
                            token = response.token,
                            refreshToken = response.refreshToken,
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.error ?: "Sign in failed",
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Network error",
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // Verify OTP
    // ═══════════════════════════════════════════════════════

    fun verifyOTP(email: String, otp: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val request = AuthModels.VerifyOTPRequest(
                    email = email,
                    otp = otp,
                )
                val response = apiService.verifyOTP(request)

                if (response.success) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        loginSuccess = true,
                        user = response.user,
                        token = response.token,
                        refreshToken = response.refreshToken,
                        message = "OTP verified successfully!",
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.error ?: "OTP verification failed",
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Network error",
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // Resend OTP
    // ═══════════════════════════════════════════════════════

    fun resendOTP(email: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val request = AuthModels.ResendOTPRequest(email = email)
                val response = apiService.resendOTP(request)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = if (response.success) "OTP resent successfully!" else response.error,
                    error = if (!response.success) response.error else null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Network error",
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // Verify Email (via token link)
    // ═══════════════════════════════════════════════════════

    fun verifyEmail(token: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val request = AuthModels.VerifyEmailRequest(token = token)
                val response = apiService.verifyEmail(request)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = if (response.success) "Email verified!" else response.error,
                    error = if (!response.success) response.error else null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Network error",
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // Resend Verification Email
    // ═══════════════════════════════════════════════════════

    fun resendVerificationEmail(email: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, message = null)

            try {
                val request = AuthModels.ResendOTPRequest(email = email)
                val response = apiService.resendVerification(request)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = if (response.success) "Verification email resent!" else response.error,
                    error = if (!response.success) response.error else null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Network error",
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // Google Sign-In
    // ═══════════════════════════════════════════════════════

    fun signInWithGoogle() {
        // TODO: Integrate Google Sign-In SDK
        // For now, show a message
        _uiState.value = _uiState.value.copy(
            error = "Google Sign-In requires SDK setup. Use email/password for now.",
        )
    }

    // ═══════════════════════════════════════════════════════
    // Get Current User
    // ═══════════════════════════════════════════════════════

    fun getCurrentUser(token: String) {
        viewModelScope.launch {
            try {
                val response = apiService.getCurrentUser("Bearer $token")
                if (response.success) {
                    _uiState.value = _uiState.value.copy(user = response.user)
                }
            } catch (_: Exception) {
                // Silently fail — user info is non-critical
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // State Management
    // ═══════════════════════════════════════════════════════

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun resetState() {
        _uiState.value = AuthUiState()
    }
}
