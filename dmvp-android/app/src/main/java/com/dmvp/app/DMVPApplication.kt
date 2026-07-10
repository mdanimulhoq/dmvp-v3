/**
 * app/src/main/java/com/dmvp/app/DMVPApplication.kt
 *
 * DMVPApplication for DMVP v3.0 Android app.
 * Custom Application class that initializes app-wide components.
 *
 * Features:
 *   - Hilt integration for dependency injection
 *   - Initialize Retrofit client
 *   - Set up logging (Timber or Android Log)
 *   - Initialize device key manager and other singletons
 *   - Configure crash reporting (optional)
 *   - Set up app-wide error handling
 *
 * Uses:
 *   - Hilt for DI
 *   - RetrofitClient for network configuration
 *   - DeviceKeyManager for key management
 *   - Timber for logging (optional)
 */

package com.dmvp.app

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

private const val TAG = "DMVPApplication"

/**
 * Main application class.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class DMVPApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize app-level components
        initializeLogging()
        initializeNetwork()
        initializeSecurity()
        initializeCrashReporting()

        Log.d(TAG, "DMVP v3.0 Application initialized")
    }

    /**
     * Initialize logging system.
     * Use Timber for structured logging if available, otherwise use Android Log.
     * In production, you might want to conditionally enable logging based on BuildConfig.DEBUG.
     */
    private fun initializeLogging() {
        // If Timber is in dependencies, plant a debug tree
        try {
            // Check if Timber is available
            val timberClass = Class.forName("timber.log.Timber")
            val plantMethod = timberClass.getMethod("plant", Any::class.java)
            // Create debug tree using the inner class name with escaped dollar sign
            val debugTreeClass = Class.forName("timber.log.Timber\$DebugTree")
            val debugTree = debugTreeClass.newInstance()
            plantMethod.invoke(null, debugTree)
            Log.d(TAG, "Timber logging initialized")
        } catch (e: ClassNotFoundException) {
            // Timber not available, fallback to Android Log
            Log.d(TAG, "Timber not found, using Android Log")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Timber", e)
        }
    }

    /**
     * Initialize network components.
     * Configure RetrofitClient with application context.
     */
    private fun initializeNetwork() {
        try {
            // Initialize RetrofitClient with application context
            // This will set up the base URL, interceptors, etc.
            com.dmvp.app.data.remote.RetrofitClient.init(this)
            Log.d(TAG, "RetrofitClient initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize RetrofitClient", e)
        }
    }

    /**
     * Initialize security components.
     * Check if device key exists; if not, it will be created on demand.
     * You could also pre-warm the keystore.
     */
    private fun initializeSecurity() {
        try {
            // Check if device key exists (optional pre-warming)
            val keyExists = com.dmvp.app.security.DeviceKeyManager.hasDeviceKey()
            Log.d(TAG, "Device key exists: $keyExists")
            if (keyExists) {
                // Get public key (just to warm up)
                val publicKey = com.dmvp.app.security.DeviceKeyManager.getPublicKey()
                Log.d(TAG, "Device key public key loaded: ${publicKey?.take(20)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize security", e)
        }
    }

    /**
     * Initialize crash reporting (optional, e.g., Firebase Crashlytics).
     * Check if crash reporting library is available and initialize it.
     */
    private fun initializeCrashReporting() {
        try {
            // Example: Firebase Crashlytics
            // val firebaseClass = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
            // val instance = firebaseClass.getMethod("getInstance").invoke(null)
            // firebaseClass.getMethod("setCrashlyticsCollectionEnabled", Boolean::class.java)
            //     .invoke(instance, true)
            // Log.d(TAG, "Firebase Crashlytics initialized")
        } catch (e: ClassNotFoundException) {
            // Firebase not available, skip
            Log.d(TAG, "Crash reporting library not found")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize crash reporting", e)
        }
    }

    /**
     * Helper to log with Timber if available, otherwise fallback to Android Log.
     */
    private fun log(priority: Int, tag: String, message: String, throwable: Throwable? = null) {
        try {
            // Attempt to use Timber
            val timberClass = Class.forName("timber.log.Timber")
            val logMethod = when (priority) {
                Log.DEBUG -> timberClass.getMethod("d", String::class.java, Any::class.java)
                Log.INFO -> timberClass.getMethod("i", String::class.java, Any::class.java)
                Log.WARN -> timberClass.getMethod("w", String::class.java, Any::class.java)
                Log.ERROR -> timberClass.getMethod("e", String::class.java, Any::class.java)
                else -> timberClass.getMethod("d", String::class.java, Any::class.java)
            }
            if (throwable != null) {
                logMethod.invoke(null, message, throwable)
            } else {
                logMethod.invoke(null, message)
            }
        } catch (e: Exception) {
            // Fallback to Android Log
            when (priority) {
                Log.DEBUG -> Log.d(tag, message, throwable)
                Log.INFO -> Log.i(tag, message, throwable)
                Log.WARN -> Log.w(tag, message, throwable)
                Log.ERROR -> Log.e(tag, message, throwable)
                else -> Log.d(tag, message, throwable)
            }
        }
    }
}
