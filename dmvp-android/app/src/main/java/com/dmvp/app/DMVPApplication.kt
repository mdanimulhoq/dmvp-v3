package com.dmvp.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

private const val TAG = "DMVPApplication"

@HiltAndroidApp
class DMVPApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        initializeLogging()
        initializeNetwork()
        initializeSecurity()
        initializeCrashReporting()

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

    private fun initializeCrashReporting() {
        try {
            Timber.tag(TAG).d("Crash reporting not configured")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to initialize crash reporting")
        }
    }
}
