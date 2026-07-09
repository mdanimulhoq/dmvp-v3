/**
 * app/src/main/java/com/dmvp/app/ui/screens/VerifyScreen.kt
 *
 * VerifyScreen for DMVP v3.0 Android app.
 * Allows users to select media, verify against the registry, and view the multi-axis verdict.
 *
 * Features:
 *   - Media selection via MediaPicker
 *   - Verification mode selection (fast, standard, deep)
 *   - Media preview with hash and fingerprint info
 *   - Multi-axis verdict display using VerdictCard
 *   - Progress and error handling
 *   - Navigation to verdict detail
 *   - Dark theme optimized
 *
 * Uses:
 *   - VerifyViewModel for state management
 *   - VerdictCard for displaying results
 *   - MediaPicker for media selection
 *   - LoadingOverlay for progress indication
 */

package com.dmvp.app.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmvp.app.data.model.DeviceTrustTier
import com.dmvp.app.ui.components.LoadingOverlay
import com.dmvp.app.ui.components.MediaPicker
import com.dmvp.app.ui.components.MediaPickerResult
import com.dmvp.app.ui.components.TrustTierBadge
import com.dmvp.app.ui.components.VerdictCard
import com.dmvp.app.ui.theme.*
import com.dmvp.app.ui.viewmodel.VerifyViewModel
import com.dmvp.app.utils.Constants

/**
 * VerifyScreen composable.
 *
 * @param onNavigateBack Callback to navigate back.
 * @param onNavigateToVerdictDetail Callback to navigate to verdict detail.
 * @param modifier Modifier for the screen.
 */
@Composable
fun VerifyScreen(
    onNavigateBack: () -> Unit,
    onNavigateToVerdictDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: VerifyViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    // If verification is successful and we have a verdict, we could auto-navigate
    // But we'll let the user click on the verdict card to navigate

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Verify Media",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.isLoading) {
                                // Optionally cancel
                            } else {
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    // Verification mode selector
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mode chips (simplified)
                        listOf(
                            Constants.MODE_FAST,
                            Constants.MODE_STANDARD,
                            Constants.MODE_DEEP
                        ).forEach { mode ->
                            FilterChip(
                                selected = uiState.verificationMode == mode,
                                onClick = {
                                    viewModel.setVerificationMode(mode)
                                },
                                label = {
                                    Text(
                                        text = mode.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                        }
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
            message = "Verifying media...",
            progress = uiState.progress,
            state = when {
                uiState.isVerified && uiState.verdict != null -> LoadingState.SUCCESS
                uiState.error != null -> LoadingState.ERROR
                else -> LoadingState.LOADING
            },
            showCancelButton = true,
            onCancel = {
                viewModel.reset()
            },
            onDismiss = {
                viewModel.clearError()
            },
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

                    // Media picker (if no file selected yet)
                    if (uiState.selectedFile == null) {
                        MediaPicker(
                            mediaType = MediaPickerType.IMAGE_AND_VIDEO,
                            onResult = { result ->
                                when (result) {
                                    is MediaPickerResult.Success -> {
                                        viewModel.setFile(result.file, result.mediaType)
                                    }
                                    is MediaPickerResult.Error -> {
                                        // Show error via viewModel
                                    }
                                    is MediaPickerResult.Cancelled -> {
                                        // Do nothing
                                    }
                                }
                            },
                            showCamera = false,
                            showGallery = true,
                            showFilePicker = true
                        )
                    }

                    // Media preview (if file selected)
                    if (uiState.selectedFile != null && uiState.mediaType != null) {
                        MediaPreviewForVerify(
                            file = uiState.selectedFile!!,
                            mediaType = uiState.mediaType!!,
                            sha256 = uiState.sha256,
                            canonicalHash = uiState.canonicalHash,
                            onClear = {
                                viewModel.reset()
                            },
                            onVerify = {
                                viewModel.verify()
                            },
                            isLoading = uiState.isLoading,
                            isVerified = uiState.isVerified,
                            hasResult = uiState.hasResult
                        )
                    }

                    // Verdict result
                    if (uiState.isVerified && uiState.verdict != null) {
                        VerdictCard(
                            verdict = uiState.verdict!!,
                            mode = VerdictCardMode.STANDARD,
                            modifier = Modifier.fillMaxWidth(),
                            onEvidenceClick = { evidenceId ->
                                onNavigateToVerdictDetail(evidenceId)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        )
    }
}

/**
 * Media preview for Verify screen with verify button.
 */
@Composable
private fun MediaPreviewForVerify(
    file: java.io.File,
    mediaType: String,
    sha256: String?,
    canonicalHash: String?,
    onClear: () -> Unit,
    onVerify: () -> Unit,
    isLoading: Boolean,
    isVerified: Boolean,
    hasResult: Boolean
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
            // Header
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
                    modifier = Modifier.size(32.dp),
                    enabled = !isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Thumbnail (placeholder)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Show placeholder
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (mediaType == Constants.MEDIA_TYPE_IMAGE) Icons.Default.Image else Icons.Default.Video,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Preview",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }

            // SHA-256
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
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onVerify,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading && !isVerified,
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
                if (isVerified && hasResult) {
                    OutlinedButton(
                        onClick = { /* Clear or re-verify */ },
                        modifier = Modifier.weight(0.5f),
                        enabled = !isLoading
                    ) {
                        Text("Clear")
                    }
                }
            }
        }
    }
}

/**
 * Extension to get readable file size.
 */
private fun java.io.File.getReadableSize(): String {
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
private fun VerifyScreenPreview() {
    DMVPTheme {
        VerifyScreen(
            onNavigateBack = {},
            onNavigateToVerdictDetail = {}
        )
    }
}
