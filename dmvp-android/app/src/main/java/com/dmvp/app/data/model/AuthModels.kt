package com.dmvp.app.data.model

import com.google.gson.annotations.SerializedName

object AuthModels {
    
    data class SignUpRequest(
        @SerializedName("email")
        val email: String,
        @SerializedName("password")
        val password: String,
        @SerializedName("name")
        val name: String?
    )
    
    data class SignInRequest(
        @SerializedName("email")
        val email: String,
        @SerializedName("password")
        val password: String
    )
    
    data class VerifyOTPRequest(
        @SerializedName("email")
        val email: String,
        @SerializedName("otp")
        val otp: String
    )
    
    data class ResendOTPRequest(
        @SerializedName("email")
        val email: String
    )
    
    data class GoogleSignInRequest(
        @SerializedName("googleToken")
        val googleToken: String
    )
    
    data class VerifyEmailRequest(
        @SerializedName("token")
        val token: String
    )
    
    data class AuthResponse(
        @SerializedName("success")
        val success: Boolean,
        @SerializedName("message")
        val message: String? = null,
        @SerializedName("error")
        val error: String? = null,
        @SerializedName("user")
        val user: UserResponse? = null,
        @SerializedName("token")
        val token: String? = null,
        @SerializedName("refreshToken")
        val refreshToken: String? = null,
        @SerializedName("requiresOTP")
        val requiresOTP: Boolean? = null,
        @SerializedName("email")
        val email: String? = null
    )
    
    data class UserResponse(
        @SerializedName("id")
        val id: String,
        @SerializedName("email")
        val email: String,
        @SerializedName("name")
        val name: String? = null,
        @SerializedName("emailVerified")
        val emailVerified: Boolean = false,
        @SerializedName("subscriptionTier")
        val subscriptionTier: String = "FREE",
        @SerializedName("zkProofsUsed")
        val zkProofsUsed: Int = 0,
        @SerializedName("zkProofsLimit")
        val zkProofsLimit: Int = 0,
        @SerializedName("createdAt")
        val createdAt: String? = null,
        @SerializedName("lastLoginAt")
        val lastLoginAt: String? = null
    )
    
    data class RefreshTokenRequest(
        @SerializedName("refreshToken")
        val refreshToken: String
    )
    
    data class RefreshTokenResponse(
        @SerializedName("success")
        val success: Boolean,
        @SerializedName("token")
        val token: String,
        @SerializedName("error")
        val error: String? = null
    )
}
