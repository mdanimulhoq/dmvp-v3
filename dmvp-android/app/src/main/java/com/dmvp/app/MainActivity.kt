/**
 * app/src/main/java/com/dmvp/app/MainActivity.kt
 *
 * MainActivity for DMVP v3.0 Android app.
 * Serves as the entry point for the application.
 *
 * Features:
 *   - Sets up Jetpack Compose UI with DMVPTheme (dark theme)
 *   - Integrates with NavGraph for navigation
 *   - Handles Dagger Hilt injection (if used)
 *   - Manages permissions for camera and storage (delegated to fragments/screens)
 *   - Handles deep links/intents for verifying evidence
 *   - Edge-to-edge display with proper insets handling
 *
 * Uses:
 *   - Hilt for dependency injection
 *   - Compose for UI
 *   - Navigation Compose for navigation
 *   - System UI controllers for status bar and navigation bar colors
 */

package com.dmvp.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.dmvp.app.navigation.NavGraph
import com.dmvp.app.ui.theme.DMVPTheme
import dagger.hilt.android.AndroidEntryPoint

private const val TAG = "MainActivity"
private const val PERMISSION_REQUEST_CODE = 100

/**
 * Main activity for the DMVP application.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        enableEdgeToEdge()

        // Request necessary permissions
        requestPermissions()

        // Set up Compose UI
        setContent {
            DMVPTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Main app content with navigation
                    AppContent()
                }
            }
        }

        // Handle deep link if any
        handleIntent(intent)
    }

    /**
     * Request permissions required by the app.
     * Camera, storage (for Android 10+), and media access.
     */
    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        // Camera permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.CAMERA)
        }

        // Storage permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses granular media permissions
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            // Android 10-12: Use READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    /**
     * Handle incoming intent (deep link, etc.)
     */
    private fun handleIntent(intent: android.content.Intent?) {
        // Check if the intent contains a URI or action for verification
        intent?.let {
            val action = it.action
            val data = it.data
            if (action == Intent.ACTION_VIEW && data != null) {
                // Handle deep link
                Log.d(TAG, "Deep link: $data")
                // Could navigate to evidence detail or verification based on URI
                // For now, just log
            }
        }
    }

    /**
     * Handle permission request results.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (i in permissions.indices) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Permission granted: ${permissions[i]}")
                } else {
                    Log.w(TAG, "Permission denied: ${permissions[i]}")
                }
            }
        }
    }
}

/**
 * App content composable containing the navigation graph.
 */
@Composable
private fun AppContent() {
    val navController = rememberNavController()
    NavGraph(
        navController = navController,
        startDestination = Screen.Home.route
    )
}
