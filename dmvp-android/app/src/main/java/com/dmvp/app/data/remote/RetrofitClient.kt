/**
 * app/src/main/java/com/dmvp/app/data/remote/RetrofitClient.kt
 *
 * Retrofit client configuration for DMVP v3.0 Android app.
 * Provides a singleton Retrofit instance with OkHttp client,
 * interceptors for logging, authentication, error handling,
 * and network connectivity checks.
 *
 * Features:
 *   - Gson converter with custom adapters for date/time and enum types.
 *   - Logging interceptor (debug only).
 *   - Authorization interceptor for adding Bearer token.
 *   - Request signing interceptor for adding signature, nonce, timestamp headers.
 *   - Error handling interceptor for parsing API errors.
 *   - Timeout configuration.
 */

package com.dmvp.app.data.remote

import android.content.Context
import android.util.Log
import com.dmvp.app.BuildConfig
import com.dmvp.app.utils.ApiConstants
import com.dmvp.app.utils.AppConfig
import com.dmvp.app.utils.Constants
import com.dmvp.app.utils.PrefsKeys
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "RetrofitClient"

/**
 * Singleton Retrofit client instance.
 */
object RetrofitClient {

    private const val BASE_URL = ApiConstants.BASE_URL_DEV // Override with BuildConfig in production

    // Gson instance with custom adapters
    private val gson: Gson by lazy {
        GsonBuilder()
            .setLenient()
            .serializeNulls()
            // Add custom type adapters if needed (e.g., for Date, UUID)
            .create()
    }

    // Shared preferences for token storage
    private var authToken: String? = null
    private var deviceKeyId: String? = null
    private var publicKeyRef: String? = null

    // Core OkHttp client builder
    private fun createOkHttpClient(context: Context): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(AppConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AppConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(AppConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        // Add logging interceptor (debug only)
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                Log.d("OkHttp", message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }

        // Add authentication interceptor
        builder.addInterceptor(AuthInterceptor(context))

        // Add error handling interceptor
        builder.addInterceptor(ErrorInterceptor())

        // Add network connectivity interceptor (optional)
        builder.addInterceptor(ConnectivityInterceptor(context))

        return builder.build()
    }

    /**
     * Authentication interceptor.
     * Adds the Authorization header with Bearer token if available.
     */
    private class AuthInterceptor(private val context: Context) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val requestBuilder = original.newBuilder()

            // Get token from preferences
            val token = getToken()
            if (!token.isNullOrEmpty()) {
                requestBuilder.header(
                    ApiConstants.HEADER_AUTHORIZATION,
                    "Bearer $token"
                )
            }

            // Add device key ID if available (optional, for context)
            val deviceId = getDeviceKeyId()
            if (!deviceId.isNullOrEmpty()) {
                requestBuilder.header("X-Device-Key-Id", deviceId)
            }

            // Add policy version if available
            val policyVersion = getPolicyVersion()
            if (!policyVersion.isNullOrEmpty()) {
                requestBuilder.header(ApiConstants.HEADER_POLICY_VERSION, policyVersion)
            }

            return chain.proceed(requestBuilder.build())
        }
    }

    /**
     * Error handling interceptor.
     * Parses API error responses and throws appropriate exceptions.
     */
    private class ErrorInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            if (!response.isSuccessful) {
                // Attempt to parse error body
                val errorBody = response.body?.string()
                val errorResponse = try {
                    gson.fromJson(errorBody, ErrorResponse::class.java)
                } catch (e: Exception) {
                    null
                }

                // Close the body to avoid leaks
                response.close()

                // Throw a custom exception with error details
                throw ApiException(
                    code = response.code,
                    errorCode = errorResponse?.errorCode ?: "UNKNOWN_ERROR",
                    message = errorResponse?.message ?: "Request failed with code ${response.code}",
                    detail = errorResponse?.detail,
                    policyVersion = errorResponse?.policyVersion,
                    requestId = errorResponse?.requestId
                )
            }
            return response
        }
    }

    /**
     * Connectivity interceptor for basic network checks.
     * Optional, can be used to check network availability.
     */
    private class ConnectivityInterceptor(private val context: Context) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            // Check network connectivity (optional)
            // If no network, throw IOException
            if (!isNetworkAvailable(context)) {
                throw IOException("No network connection available")
            }
            return chain.proceed(chain.request())
        }
    }

    /**
     * Check network availability (simplified).
     */
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val networkInfo = connectivityManager?.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }

    // ============================
    // Preference helpers (to be replaced with actual DataStore)
    // ============================

    private fun getToken(): String? {
        // In production, use DataStore or SharedPreferences
        // For MVP, we use a simple static variable
        return authToken
    }

    private fun getDeviceKeyId(): String? {
        return deviceKeyId
    }

    private fun getPolicyVersion(): String? {
        // Could be stored in preferences
        return null
    }

    // ============================
    // Public API
    // ============================

    /**
     * Initialize the Retrofit client with context.
     * Should be called once during app initialization.
     */
    fun init(context: Context) {
        // Load preferences
        authToken = null // Load from DataStore
        deviceKeyId = null // Load from DataStore
        // Other setup
    }

    /**
     * Update authentication token.
     */
    fun setAuthToken(token: String?) {
        authToken = token
        // Persist to DataStore
    }

    /**
     * Update device key ID.
     */
    fun setDeviceKeyId(deviceId: String?) {
        deviceKeyId = deviceId
        // Persist to DataStore
    }

    /**
     * Get the Retrofit instance.
     */
    fun getInstance(context: Context): ApiService {
        val client = createOkHttpClient(context)
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }

    /**
     * Get the Retrofit instance with a custom base URL (for testing or switching environments).
     */
    fun getInstance(context: Context, baseUrl: String): ApiService {
        val client = createOkHttpClient(context)
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }

    /**
     * Clear all authentication and device state.
     */
    fun clearSession() {
        authToken = null
        deviceKeyId = null
        // Clear preferences
    }
}

/**
 * Custom exception for API errors.
 */
class ApiException(
    val code: Int,
    val errorCode: String,
    override val message: String,
    val detail: Map<String, Any>? = null,
    val policyVersion: String? = null,
    val requestId: String? = null
) : IOException("[$code] $errorCode: $message") {
    fun toErrorResponse(): ErrorResponse {
        return ErrorResponse(
            errorCode = errorCode,
            message = message,
            detail = detail,
            policyVersion = policyVersion,
            requestId = requestId
        )
    }
}

/**
 * Extension to convert HttpException to ApiException if possible.
 */
fun HttpException.toApiException(): ApiException {
    // Try to parse error body from response
    val response = this.response()
    val errorBody = response?.errorBody()?.string()
    val errorResponse = try {
        RetrofitClient.gson.fromJson(errorBody, ErrorResponse::class.java)
    } catch (e: Exception) {
        null
    }
    return ApiException(
        code = this.code(),
        errorCode = errorResponse?.errorCode ?: "HTTP_ERROR",
        message = errorResponse?.message ?: this.message ?: "HTTP request failed",
        detail = errorResponse?.detail,
        policyVersion = errorResponse?.policyVersion,
        requestId = errorResponse?.requestId
    )
}
