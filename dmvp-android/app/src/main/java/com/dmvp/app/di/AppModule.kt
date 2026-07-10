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
import com.dmvp.app.data.repository.DMVPRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val PREFERENCES_NAME = "dmvp_preferences"
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = PREFERENCES_NAME)

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provide repository.
     * DMVPRepository only requires context; all other dependencies are handled internally.
     */
    @Singleton
    @Provides
    fun provideDMVPRepository(
        @ApplicationContext context: Context
    ): DMVPRepository {
        return DMVPRepository(context)
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
