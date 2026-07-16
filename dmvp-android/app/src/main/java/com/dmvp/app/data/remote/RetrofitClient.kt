/**
 * app/src/main/java/com/dmvp/app/data/remote/RetrofitClient.kt
 *
 * Singleton Retrofit client for DMVP v3.0 Android app.
 * Builds and caches the ApiService instance, tracks the current
 * device key ID, and manages session state.
 */

package com.dmvp.app.data.remote

import android.content.Context
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import timber.log.Timber

/**
 * Custom exception for API errors returned by the DMVP backend.
 * Carries an optional backend error code alongside the message.
 */
class ApiException(
    val errorCode: String? = null,
    override val message: String? = null
) : Exception(message)

/**
 * Singleton object that builds and provides the Retrofit-backed ApiService.
 */
object RetrofitClient {

    private const val BASE_URL = "https://dmvp-v3-1.onrender.com/api/v1/"

    private var retrofit: Retrofit? = null
    private var apiService: ApiService? = null
    private var deviceKeyId: String? = null

    /**
     * Initialize the Retrofit client. Safe to call multiple times;
     * only builds once.
     */
    fun init() {
        if (retrofit == null) {
            Timber.d("Initializing Retrofit client")

            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            apiService = retrofit!!.create(ApiService::class.java)
            Timber.d("Retrofit client initialized successfully")
        }
    }

    /**
     * Get the singleton ApiService instance. Initializes the client
     * automatically if it hasn't been initialized yet.
     */
    fun getInstance(context: Context): ApiService {
        if (apiService == null) {
            init()
        }

        return apiService!!
    }

    /**
     * Store the current device key ID.
     */
    fun setDeviceKeyId(deviceKeyId: String) {
        this.deviceKeyId = deviceKeyId
        Timber.d("Device key ID set: $deviceKeyId")
    }

    /**
     * Get the current device key ID, if any.
     */
    fun getDeviceKeyId(): String? = deviceKeyId

    /**
     * Clear session state, e.g. on device key revocation.
     */
    fun clearSession() {
        deviceKeyId = null
        Timber.d("Session cleared")
    }
}
