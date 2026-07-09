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
import com.dmvp.app.navigation.Screen
import com.dmvp.app.ui.theme.DMVPTheme
import dagger.hilt.android.AndroidEntryPoint
import android.content.Intent

private const val TAG = "MainActivity"
private const val PERMISSION_REQUEST_CODE = 100

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissions()
        setContent {
            DMVPTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent()
                }
            }
        }
        handleIntent(intent)
    }

    private fun requestPermissions() {
        val permissions = mutableListOf()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) = PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) = PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) = PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) = PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            val action = it.action
            val data = it.data
            if (action == Intent.ACTION_VIEW && data = null) {
                Log.d(TAG, "Deep link: $data")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array(out String),
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (i in permissions.indices) {
                if (grantResults(i) == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Permission granted: $^{permissions^(i^)^}")
                } else {
                    Log.w(TAG, "Permission denied: $^{permissions^(i^)^}")
                }
            }
        }
    }
}

@Composable
private fun AppContent() {
    val navController = rememberNavController()
    NavGraph(navController = navController, startDestination = Screen.Home.route)
}
