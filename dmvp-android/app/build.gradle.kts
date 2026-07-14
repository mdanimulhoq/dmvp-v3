/**
 * app/build.gradle.kts
 *
 * Android app module build configuration for DMVP v3.0.
 * Uses Kotlin, Jetpack Compose, CameraX, Retrofit, and other modern libraries.
 * minSdk = 26 (Android 8.0), targetSdk = 34 (Android 14).
 */

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.dmvp.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dmvp.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "3.0.0"

        // DMVP Backend API Base URL (Render deployment)
        buildConfigField(
            "String",
            "DMVP_API_BASE_URL",
            "\"https://dmvp-v3.onrender.com\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            // Enable more logging, etc.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        viewBinding = false
        // Enable build config for version info
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose (Material 3)
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // For dark theme support and theming
    implementation("androidx.compose.material3:material3-window-size-class")
    // Foundation (clickable, animations, layout building blocks)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // ViewModel and Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    // Needed for ListenableFuture.await() used with CameraX's ProcessCameraProvider
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.7.3")

    // Dagger Hilt for dependency injection
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    implementation("androidx.camera:camera-video:1.3.1")

    // Networking: Retrofit + OkHttp + Gson
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Security: Android Keystore (already in Android SDK)

    // Image loading: Coil with Compose
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Perceptual hash (pHash) â€“ we'll use a library or custom implementation.
    // For Android, we can use the pHash library if available, but we'll use a custom utility.
    // We'll include a pure-Kotlin implementation or use OpenCV? But OpenCV is heavy.
    // For MVP, we'll rely on a simple implementation using Android's Bitmap and a custom hash.
    // No external library needed for pHash, we'll implement in code.

    // Video processing: for keyframe extraction, we may use MediaCodec or ExoPlayer.
    // For simplicity, we'll use Android's MediaMetadataRetriever.
    // We'll include ExoPlayer if needed for deep analysis, but for now not.

    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
