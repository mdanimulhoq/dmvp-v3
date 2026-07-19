/**
 * Phase 3 Step 3.8: Verify Repository
 * Handles API calls for 10-layer verification
 */

package com.dmvp.app.data.repository

import com.dmvp.app.data.model.VerificationVerdict
import com.dmvp.app.data.remote.ApiService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VerifyRepository @Inject constructor(
    private val apiService: ApiService
) {
    /**
     * Verify an asset by uploading image data
     */
    suspend fun verifyAsset(imageData: ByteArray): VerificationVerdict {
        val imageBody = imageData.toRequestBody("image/*".toMediaType())
        val multipartBody = MultipartBody.Part.createFormData("file", "image.jpg", imageBody)

        return apiService.verifyAsset(multipartBody)
    }

    /**
     * Verify by evidence ID
     */
    suspend fun verifyByEvidenceId(evidenceId: String): VerificationVerdict {
        return apiService.verifyByEvidenceId(evidenceId)
    }

    /**
     * Get L8 AI derivative detection result
     */
    suspend fun detectAIDerivative(imageData: ByteArray): Map<String, Any> {
        val imageBody = imageData.toRequestBody("image/*".toMediaType())
        val multipartBody = MultipartBody.Part.createFormData("file", "image.jpg", imageBody)

        return apiService.detectAIDerivative(multipartBody)
    }
}
