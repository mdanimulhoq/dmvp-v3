package com.dmvp.app.data.remote

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.tasks.await

/**
 * Google Sign-In Manager
 * Handles Google Sign-In flow using Credential Manager API
 */
class GoogleSignInManager(private val context: Context) {

    companion object {
        private const val TAG = "GoogleSignInManager"
        // TODO: Replace with your actual Web Client ID from Google Cloud Console
        private const val WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID_HERE"
    }

    private val credentialManager = CredentialManager.create(context)

    /**
     * Data class to hold Google Sign-In result
     */
    data class GoogleSignInResult(
        val success: Boolean,
        val idToken: String? = null,
        val email: String? = null,
        val displayName: String? = null,
        val errorMessage: String? = null
    )

    /**
     * Initiates Google Sign-In flow
     * @return GoogleSignInResult with token and user info if successful
     */
    suspend fun signIn(): GoogleSignInResult {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = context,
                request = request
            )

            val credential = result.credential
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

            GoogleSignInResult(
                success = true,
                idToken = googleIdTokenCredential.idToken,
                email = googleIdTokenCredential.id,
                displayName = googleIdTokenCredential.displayName
            )
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google Sign-In failed: ${e.message}", e)
            GoogleSignInResult(
                success = false,
                errorMessage = "Sign-in failed: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during Google Sign-In", e)
            GoogleSignInResult(
                success = false,
                errorMessage = "Unexpected error: ${e.message}"
            )
        }
    }
}
