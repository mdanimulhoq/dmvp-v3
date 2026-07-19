/**
 * Phase 3 Step 3.5: Search Repository
 * Handles API calls for cross-modal search
 */

package com.dmvp.app.data.repository

import com.dmvp.app.data.model.SearchResponse
import com.dmvp.app.data.model.SearchResult
import com.dmvp.app.data.remote.ApiService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val apiService: ApiService
) {
    /**
     * Search by text query
     */
    suspend fun searchByText(
        query: String,
        vectorType: String = "siglip",
        limit: Int = 20,
        threshold: Float = 0.7f
    ): List<SearchResult> {
        val requestBody = mapOf(
            "query_type" to "text",
            "query" to query,
            "vector_type" to vectorType,
            "limit" to limit,
            "threshold" to threshold
        )

        val response = apiService.crossModalSearch(requestBody)
        return if (response.success) {
            response.results
        } else {
            emptyList()
        }
    }

    /**
     * Search by image
     */
    suspend fun searchByImage(
        imageData: ByteArray,
        vectorType: String = "siglip",
        limit: Int = 20,
        threshold: Float = 0.7f
    ): List<SearchResult> {
        val imageBody = imageData.toRequestBody("image/*".toMediaType())
        val multipartBody = MultipartBody.Part.createFormData("file", "image.jpg", imageBody)

        val requestBody = mapOf(
            "query_type" to "image",
            "vector_type" to vectorType,
            "limit" to limit,
            "threshold" to threshold
        )

        val response = apiService.crossModalSearchWithImage(requestBody, multipartBody)
        return if (response.success) {
            response.results
        } else {
            emptyList()
        }
    }

    /**
     * Get search statistics
     */
    suspend fun getSearchStats(): Map<String, Any> {
        return try {
            apiService.getSearchStats()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
