/**
 * app/src/main/java/com/dmvp/app/di/AppModule.kt
 *
 * Hilt dependency injection module for app-wide singletons.
 * Provides instances of:
 *   - Retrofit client
 *   - API services
 *   - Repository
 *   - Utilities
 */

package com.dmvp.app.di

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.dmvp.app.data.remote.ApiService
import com.dmvp.app.data.remote.RetrofitClient
import com.dmvp.app.data.repository.DMVPRepository
import com.dmvp.app.security.DeviceKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

private const val PREFERENCES_NAME = "dmvp_preferences"
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = PREFERENCES_NAME)

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provide Retrofit instance.
     */
    @Singleton
    @Provides
    fun provideRetrofit(@ApplicationContext context: Context): Retrofit {
        return RetrofitClient.getRetrofitInstance(context)
    }

    /**
     * Provide API service.
     */
    @Singleton
    @Provides
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }

    /**
     * Provide repository.
     */
    @Singleton
    @Provides
    fun provideDMVPRepository(
        apiService: ApiService,
        @ApplicationContext context: Context,
        deviceKeyManager: DeviceKeyManager
    ): DMVPRepository {
        return DMVPRepository(apiService, context, deviceKeyManager)
    }

    /**
     * Provide device key manager.
     */
    @Singleton
    @Provides
    fun provideDeviceKeyManager(@ApplicationContext context: Context): DeviceKeyManager {
        return DeviceKeyManager(context)
    }

    /**
     * Provide shared preferences.
     */
    @Singleton
    @Provides
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Provide DataStore preferences.
     */
    @Singleton
    @Provides
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
}
