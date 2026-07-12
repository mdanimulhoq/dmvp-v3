package com.dmvp.app

import android.app.Application
import com.dmvp.app.data.repository.DMVPRepository
import com.dmvp.app.data.repository.Result
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

private const val TAG = "DMVPApplication"

@HiltAndroidApp
class DMVPApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        initializeLogging()
        initializeNetwork()
        initializeSecurity()
        initializeCrashReporting()
        initializeDeviceRegistration()

        Timber.tag(TAG).d("DMVP v3.0 Application initialized")
    }

    private fun initializeLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.tag(TAG).d("Timber logging initialized")
    }

    private fun initializeNetwork() {
        try {
            com.dmvp.app.data.remote.RetrofitClient.init()
            Timber.tag(TAG).d("RetrofitClient initialized")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to initialize RetrofitClient")
        }
    }

    private fun initializeSecurity() {
        try {
            val keyExists = com.dmvp.app.security.DeviceKeyManager.hasDeviceKey()
            Timber.tag(TAG).d("Device key exists: $keyExists")

            if (keyExists) {
                val publicKey = com.dmvp.app.security.DeviceKeyManager.getPublicKey()
                Timber.tag(TAG).d("Device key public key loaded: ${publicKey?.take(20)}")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to initialize security")
        }
    }

    private fun initializeDeviceRegistration() {
        applicationScope.launch {
            try {
                Timber.tag(TAG).d("Auto device registration started")

                val repository = DMVPRepository(applicationContext)

                when (val result = repository.getOrCreateDeviceKey()) {
                    is Result.Success -> {
                        val deviceKeyId = result.data.first
                        val trustTier = repository.getCachedTrustTier() ?: "unknown"

                        Timber.tag(TAG).d(
                            "Auto device registration confirmed: deviceKeyId=$deviceKeyId trustTier=$trustTier"
                        )
                    }

                    is Result.Error -> {
                        Timber.tag(TAG).e(
                            result.exception,
                            "Auto device registration failed: errorCode=${result.errorCode} message=${result.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Auto device registration failed safely")
            }
        }
    }

    private fun initializeCrashReporting() {
        try {
            Timber.tag(TAG).d("Crash reporting not configured")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to initialize crash reporting")
        }
    }
}
