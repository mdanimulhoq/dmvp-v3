/**
 * app/src/main/java/com/dmvp/app/ui/screens/CaptureScreen.kt
 *
 * CaptureScreen for DMVP v3.0 Android app.
 * Allows users to capture or select media, preview it, and choose to register or verify.
 *
 * Features:
 *   - MediaPicker for camera/gallery selection
 *   - Media preview (image/video thumbnail)
 *   - Media metadata display (type, size, SHA-256)
 *   - Privacy flags toggle (GPS, EXIF, Device Info)
 *   - Register and Verify action buttons
 *   - Loading states and error handling
 *   - Dark theme optimized
 *
 * Uses:
 *   - CaptureViewModel for state management
 *   - MediaPicker for media selection
 *   - LoadingOverlay for progress indication
 */

package com.dmvp.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.dmvp.app.R
import com.dmvp.app.data.model.PrivacyFlags
import com.dmvp.app.ui.components.LoadingOverlay
import com.dmvp.app.ui.components.MediaPicker
import com.dmvp.app.ui.components.MediaPickerResult
import com.dmvp.app.ui.components.TrustTierBadge
import com.dmvp.app.ui.theme.*
import com.dmvp.app.ui.viewmodel.CaptureViewModel
import com.dmvp.app.utils.Constants
import com.dmvp.app.utils.toFile
import java.io.File

/**
 * CaptureScreen composable.
 *
 * @param onNavigateBack Callback to navigate back to home.
 * @param onNavigateToRegister Callback to navigate to register screen (with file info).
 * @param onNavigateToVerify Callback to navigate to verify screen (with file info).
 * @param modifier Modifier for the screen.
 */
@Composable
fun CaptureScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRegister: (File, String) -> Unit,
    onNavigateToVerify: (File, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: CaptureViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Launcher for file picker (additional)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val file = it.toFile(context)
                if (file != null) {
                    val mimeType = context.contentResolver.getType(it) ?: "image/*"
                    val mediaType = if (mimeType.startsWith("video/")) Constants.MEDIA_TYPE_VIDEO else Constants.MEDIA_TYPE_IMAGE
                    viewModel.setGalleryFile(file, mediaType)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Capture Media",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LoadingOverlay(
            isLoading = uiState.isLoading,
            message = when {
                uiState.isCapturing -> "Capturing..."
                uiState.validationMode == com.dmvp.app.ui.viewmodel.ValidationMode.PROCESSING -> "Processing media..."
                uiState.isLoading -> "Loading..."
                else -> null
            },
            progress = uiState.progress,
            state = when {
                uiState.isRegistered -> LoadingState.SUCCESS
                uiState.error != null -> LoadingState.ERROR
                else -> LoadingState.LOADING
            },
            showCancelButton = true,
            onCancel = { viewModel.resetCapture() },
            onDismiss = { viewModel.clearError() },
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Error message
                    if (uiState.error != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Error.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Error",
                                    tint = Error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = uiState.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Error,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = viewModel::clearError,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = Error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // MediaPicker
                    if (uiState.selectedFile == null && !uiState.isCapturing) {
                        MediaPicker(
                            mediaType = MediaPickerType.IMAGE_AND_VIDEO,
                            onResult = { result ->
                                when (result) {
                                    is MediaPickerResult.Success -> {
                                        if (result.mediaType == Constants.MEDIA_TYPE_IMAGE) {
                                            viewModel.setGalleryFile(result.file, result.mediaType)
                                        } else {
                                            viewModel.setGalleryFile(result.file, result.mediaType)
                                        }
                                    }
                                    is MediaPickerResult.Error -> {
                                        // Show error
                                    }
                                    is MediaPickerResult.Cancelled -> {
                                        // User cancelled
                                    }
                                }
                            },
                            showCamera = true,
                            showGallery = true,
                            showFilePicker = true
                        )
                    }

                    // Media preview
                    if (uiState.selectedFile != null) {
                        MediaPreview(
                            file = uiState.selectedFile!!,
                            mediaType = uiState.mediaType ?: Constants.MEDIA_TYPE_IMAGE,
                            sha256 = uiState.sha256,
                            canonicalHash = uiState.canonicalHash,
                            onClear = {
                                viewModel.resetCapture()
                            }
                        )

                        // Privacy flags
                        PrivacyFlagsSection(
                            privacyFlags = uiState.privacyFlags,
                            onPrivacyFlagsChanged = { viewModel.setPrivacyFlags(it) }
                        )

                        // Action buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    uiState.selectedFile?.let { file ->
                                        val mediaType = uiState.mediaType ?: Constants.MEDIA_TYPE_IMAGE
                                        onNavigateToRegister(file, mediaType)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = uiState.validationMode == com.dmvp.app.ui.viewmodel.ValidationMode.READY_FOR_REGISTRATION ||
                                        uiState.validationMode == com.dmvp.app.ui.viewmodel.ValidationMode.COMPLETE,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyanBright,
                                    contentColor = DeepPurple900
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Register")
                            }
                            Button(
                                onClick = {
                                    uiState.selectedFile?.let { file ->
                                        val mediaType = uiState.mediaType ?: Constants.MEDIA_TYPE_IMAGE
                                        onNavigateToVerify(file, mediaType)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = uiState.sha256 != null,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Success,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Verify")
                            }
                        }

                        // Additional info: device trust tier
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Device Trust:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            // Get trust tier from repository or use placeholder
                            TrustTierBadge(
                                trustTier = DeviceTrustTier.TIER_A, // placeholder
                                size = TrustTierBadgeSize.SMALL
                            )
                        }
                    }

                    // Additional spacing at bottom
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        )
    }
}

/**
 * Media preview component.
 */
@Composable
private fun MediaPreview(
    file: File,
    mediaType: String,
    sha256: String?,
    canonicalHash: String?,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with clear button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (mediaType == Constants.MEDIA_TYPE_IMAGE) Icons.Default.Image else Icons.Default.Video,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (mediaType == Constants.MEDIA_TYPE_IMAGE) "Image" else "Video",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "•",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Text(
                        text = file.getReadableSize(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (mediaType == Constants.MEDIA_TYPE_IMAGE) {
                    AsyncImage(
                        model = file,
                        contentDescription = "Media preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Video thumbnail - use a placeholder
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleFilled,
                            contentDescription = "Video",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
            }

            // Hash info
            if (sha256 != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SHA-256:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = sha256.take(16) + "..." + sha256.takeLast(8),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (canonicalHash != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Canonical:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = canonicalHash.take(16) + "..." + canonicalHash.takeLast(8),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Privacy flags section.
 */
@Composable
private fun PrivacyFlagsSection(
    privacyFlags: PrivacyFlags,
    onPrivacyFlagsChanged: (PrivacyFlags) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Privacy Flags",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FilterChip(
                    selected = privacyFlags.gps,
                    onClick = {
                        onPrivacyFlagsChanged(
                            privacyFlags.copy(gps = !privacyFlags.gps)
                        )
                    },
                    label = { Text("GPS") },
                    leadingIcon = if (privacyFlags.gps) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                FilterChip(
                    selected = privacyFlags.exif,
                    onClick = {
                        onPrivacyFlagsChanged(
                            privacyFlags.copy(exif = !privacyFlags.exif)
                        )
                    },
                    label = { Text("EXIF") },
                    leadingIcon = if (privacyFlags.exif) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                FilterChip(
                    selected = privacyFlags.deviceInfo,
                    onClick = {
                        onPrivacyFlagsChanged(
                            privacyFlags.copy(deviceInfo = !privacyFlags.deviceInfo)
                        )
                    },
                    label = { Text("Device Info") },
                    leadingIcon = if (privacyFlags.deviceInfo) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }
    }
}

/**
 * Extension to get readable file size.
 */
private fun File.getReadableSize(): String {
    val size = this.length()
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
        size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
    }
}

// ================================
// Preview
// ================================

@Preview(showBackground = true, backgroundColor = 0xFF1A0033)
@Composable
private fun CaptureScreenPreview() {
    DMVPTheme {
        CaptureScreen(
            onNavigateBack = {},
            onNavigateToRegister = { _, _ -> },
            onNavigateToVerify = { _, _ -> }
        )
    }
}
