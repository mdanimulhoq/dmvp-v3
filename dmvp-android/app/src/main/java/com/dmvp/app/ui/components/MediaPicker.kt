/**
 * app/src/main/java/com/dmvp/app/ui/components/MediaPicker.kt
 *
 * MediaPicker composable for DMVP v3.0 Android app.
 * Handles media selection from camera and gallery with permission management.
 *
 * Features:
 *   - Camera capture (image and video)
 *   - Gallery selection (image and video)
 *   - Permission handling (camera, storage, media)
 *   - File management (saving to app cache)
 *   - Progress and error states
 *   - Dark theme optimized
 *
 * Uses:
 *   - CameraX for camera capture
 *   - ActivityResultContracts for gallery and camera intents
 *   - FileProvider for file sharing
 *   - Android 13+ media permissions
 */

package com.dmvp.app.ui.components

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.dmvp.app.BuildConfig
import com.dmvp.app.R
import com.dmvp.app.utils.AppConfig
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "MediaPicker"
private const val FILE_PROVIDER_AUTHORITY = "${BuildConfig.APPLICATION_ID}.fileprovider"

/**
 * Media type for selection.
 */
enum class MediaPickerType {
    IMAGE,
    VIDEO,
    IMAGE_AND_VIDEO
}

/**
 * Media picker result.
 */
sealed class MediaPickerResult {
    data class Success(val file: File, val mediaType: String, val uri: Uri? = null) : MediaPickerResult()
    data class Error(val message: String, val errorCode: String? = null) : MediaPickerResult()
    object Cancelled : MediaPickerResult()
}

/**
 * Main MediaPicker composable.
 * Shows a grid of options: Camera (photo/video), Gallery (photo/video), and optional file picker.
 *
 * @param modifier Modifier for the container.
 * @param mediaType Type of media to pick (image, video, or both).
 * @param onResult Callback with the picker result.
 * @param showCamera Whether to show the camera option (default: true).
 * @param showGallery Whether to show the gallery option (default: true).
 * @param showFilePicker Whether to show the file picker option (default: false).
 */
@Composable
fun MediaPicker(
    modifier: Modifier = Modifier,
    mediaType: MediaPickerType = MediaPickerType.IMAGE,
    onResult: (MediaPickerResult) -> Unit,
    showCamera: Boolean = true,
    showGallery: Boolean = true,
    showFilePicker: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCameraVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Text(
            text = "Select Media",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Grid of options
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            if (showCamera) {
                MediaPickerOption(
                    icon = Icons.Default.CameraAlt,
                    label = "Camera",
                    onClick = { isCameraVisible = !isCameraVisible }
                )
            }
            if (showGallery) {
                MediaPickerOption(
                    icon = Icons.Default.PhotoLibrary,
                    label = "Gallery",
                    onClick = {
                        launchGalleryPicker(
                            context = context,
                            mediaType = mediaType,
                            onResult = onResult
                        )
                    }
                )
            }
            if (showFilePicker) {
                MediaPickerOption(
                    icon = Icons.Default.Folder,
                    label = "Browse",
                    onClick = {
                        launchFilePicker(
                            context = context,
                            mediaType = mediaType,
                            onResult = onResult
                        )
                    }
                )
            }
        }

        // Camera view (inline)
        if (isCameraVisible) {
            Spacer(modifier = Modifier.height(16.dp))
            CameraView(
                context = context,
                mediaType = mediaType,
                onResult = onResult,
                onClose = { isCameraVisible = false }
            )
        }
    }
}

/**
 * Media picker option button.
 */
@Composable
private fun MediaPickerOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Camera view composable using CameraX.
 */
@Composable
private fun CameraView(
    context: Context,
    mediaType: MediaPickerType,
    onResult: (MediaPickerResult) -> Unit,
    onClose: () -> Unit
) {
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var videoCapture: VideoCapture<Recorder>? by remember { mutableStateOf(null) }
    var isRecording by remember { mutableStateOf(false) }
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var previewView: PreviewView? by remember { mutableStateOf(null) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
    }

    // Request camera permission if not granted
    LaunchedEffect(Unit) {
        if (!cameraPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Initialize camera
    LaunchedEffect(cameraPermissionGranted) {
        if (cameraPermissionGranted) {
            try {
                val cameraProvider = ProcessCameraProvider.getInstance(context).await()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView?.surfaceProvider)
                }

                // Select camera
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Image capture
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                // Video capture
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            Quality.HIGHEST,
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                        )
                    )
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                // Bind use cases
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    videoCapture
                )

            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
                onResult(MediaPickerResult.Error("Camera initialization failed: ${e.message}"))
                onClose()
            }
        }
    }

    // UI
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .clip(RoundedCornerShape(12.dp))
    ) {
        // Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Color.Black)
        ) {
            if (cameraPermissionGranted) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            previewView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Camera permission required",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close button
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            // Capture button
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .border(
                        width = 4.dp,
                        color = Color.White,
                        shape = CircleShape
                    )
                    .background(Color.Transparent)
                    .clickable {
                        if (isRecording) {
                            // Stop recording
                            videoCapture?.stopRecording()
                            isRecording = false
                        } else {
                            captureMedia(
                                context = context,
                                mediaType = mediaType,
                                imageCapture = imageCapture,
                                videoCapture = videoCapture,
                                onResult = { result ->
                                    onResult(result)
                                    if (result !is MediaPickerResult.Cancelled) {
                                        onClose()
                                    }
                                }
                            )
                        }
                    }
            )

            // Toggle flash (placeholder)
            IconButton(onClick = { /* Toggle flash */ }) {
                Icon(
                    imageVector = Icons.Default.FlashOn,
                    contentDescription = "Flash",
                    tint = Color.White
                )
            }

            // Toggle camera (front/back) - placeholder
            IconButton(onClick = { /* Toggle camera */ }) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch camera",
                    tint = Color.White
                )
            }
        }

        // Recording indicator
        if (isRecording) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recording...",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * Capture media (image or video) based on media type.
 */
private fun captureMedia(
    context: Context,
    mediaType: MediaPickerType,
    imageCapture: ImageCapture?,
    videoCapture: VideoCapture<Recorder>?,
    onResult: (MediaPickerResult) -> Unit
) {
    try {
        val file = createMediaFile(context, if (mediaType == MediaPickerType.VIDEO) "video" else "image")

        when {
            mediaType == MediaPickerType.VIDEO && videoCapture != null -> {
                // Record video
                val outputOptions = VideoCapture.OutputFileOptions.Builder(file).build()
                videoCapture.startRecording(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : VideoCapture.OnVideoSavedCallback {
                        override fun onVideoSaved(output: VideoCapture.OutputFileResults) {
                            onResult(
                                MediaPickerResult.Success(
                                    file = file,
                                    mediaType = "video",
                                    uri = output.savedUri ?: Uri.fromFile(file)
                                )
                            )
                        }

                        override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                            Log.e(TAG, "Video capture error: $message")
                            onResult(MediaPickerResult.Error("Video capture failed: $message"))
                        }
                    }
                )
            }

            mediaType != MediaPickerType.VIDEO && imageCapture != null -> {
                // Capture image
                val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            onResult(
                                MediaPickerResult.Success(
                                    file = file,
                                    mediaType = "image",
                                    uri = output.savedUri ?: Uri.fromFile(file)
                                )
                            )
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "Image capture error", exception)
                            onResult(MediaPickerResult.Error("Image capture failed: ${exception.message}"))
                        }
                    }
                )
            }

            else -> {
                onResult(MediaPickerResult.Error("Camera not ready"))
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Capture error", e)
        onResult(MediaPickerResult.Error("Capture error: ${e.message}"))
    }
}

/**
 * Launch gallery picker.
 */
private fun launchGalleryPicker(
    context: Context,
    mediaType: MediaPickerType,
    onResult: (MediaPickerResult) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) {
            onResult(MediaPickerResult.Cancelled)
            return@rememberLauncherForActivityResult
        }
        try {
            val file = uriToFile(context, uri) ?: throw Exception("Failed to convert URI to file")
            val mimeType = context.contentResolver.getType(uri) ?: "image/*"
            val mediaType = if (mimeType.startsWith("video/")) "video" else "image"
            onResult(MediaPickerResult.Success(file, mediaType, uri))
        } catch (e: Exception) {
            Log.e(TAG, "Gallery pick error", e)
            onResult(MediaPickerResult.Error("Failed to load media: ${e.message}"))
        }
    }

    val mimeType = when (mediaType) {
        MediaPickerType.IMAGE -> "image/*"
        MediaPickerType.VIDEO -> "video/*"
        MediaPickerType.IMAGE_AND_VIDEO -> "*/*"
    }

    launcher.launch(mimeType)
}

/**
 * Launch file picker (Android's built-in file picker).
 */
private fun launchFilePicker(
    context: Context,
    mediaType: MediaPickerType,
    onResult: (MediaPickerResult) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) {
            onResult(MediaPickerResult.Cancelled)
            return@rememberLauncherForActivityResult
        }
        try {
            val file = uriToFile(context, uri) ?: throw Exception("Failed to convert URI to file")
            val mimeType = context.contentResolver.getType(uri) ?: "image/*"
            val mediaType = if (mimeType.startsWith("video/")) "video" else "image"
            onResult(MediaPickerResult.Success(file, mediaType, uri))
        } catch (e: Exception) {
            Log.e(TAG, "File pick error", e)
            onResult(MediaPickerResult.Error("Failed to load file: ${e.message}"))
        }
    }

    val mimeType = when (mediaType) {
        MediaPickerType.IMAGE -> "image/*"
        MediaPickerType.VIDEO -> "video/*"
        MediaPickerType.IMAGE_AND_VIDEO -> "*/*"
    }

    launcher.launch(mimeType)
}

/**
 * Create a media file in the app's cache directory.
 */
private fun createMediaFile(context: Context, type: String): File {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val fileName = "DMVP_${type}_$timestamp.${if (type == "image") "jpg" else "mp4"}"
    val storageDir = context.cacheDir
    return File(storageDir, fileName).apply {
        parentFile?.mkdirs()
    }
}

/**
 * Convert URI to File (using content resolver).
 */
private fun uriToFile(context: Context, uri: Uri): File? {
    return try {
        val fileName = getFileNameFromUri(context, uri) ?: "temp_file"
        val file = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        file
    } catch (e: Exception) {
        Log.e(TAG, "URI to file conversion failed", e)
        null
    }
}

/**
 * Get file name from URI.
 */
private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        if (it.moveToFirst()) {
            return it.getString(nameIndex)
        }
    }
    return null
}

/**
 * Preview function for MediaPicker.
 */
@Preview(showBackground = true, backgroundColor = 0xFF1A0033)
@Composable
private fun MediaPickerPreview() {
    DMVPTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MediaPicker(
                onResult = { /* Preview */ }
            )
        }
    }
}
