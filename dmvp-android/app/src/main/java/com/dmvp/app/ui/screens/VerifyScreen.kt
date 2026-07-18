/**
 * app/src/main/java/com/dmvp/app/ui/screens/VerifyScreen.kt
 *
 * VerifyScreen for DMVP v3.0 Android app.
 * Allows users to select media, verify against the registry, and view the multi-axis verdict.
 */

package com.dmvp.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmvp.app.ui.components.LoadingOverlay
import com.dmvp.app.ui.components.LoadingState
import com.dmvp.app.ui.components.MediaPicker
import com.dmvp.app.ui.components.MediaPickerResult
import com.dmvp.app.ui.components.MediaPickerType
import com.dmvp.app.ui.components.VerdictCard
import com.dmvp.app.ui.components.VerdictCardMode
import com.dmvp.app.ui.theme.DMVPTheme
import com.dmvp.app.ui.theme.Error
import com.dmvp.app.ui.theme.Success
import com.dmvp.app.ui.viewmodel.VerifyViewModel
import com.dmvp.app.utils.DmvpConstants
import com.dmvp.app.utils.VerificationConstants
import timber.log.Timber

/**
 * VerifyScreen composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyScreen(
    onNavigateBack: () -> Unit,
    onNavigateToVerdictDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: VerifyViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

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
                            if (!uiState.isLoading) {
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val modes: List<String> = listOf(
                            VerificationConstants.MODE_FAST,
                            VerificationConstants.MODE_STANDARD,
                            VerificationConstants.MODE_DEEP
                        )
                        modes.forEach { mode ->
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
                    val errorMsg = uiState.error
                    if (errorMsg != null) {
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
                                    text = errorMsg,
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

                    // ── Step 3.4: MediaPicker with camera support ──────────
                    if (uiState.selectedFile == null) {
                        MediaPicker(
                            mediaType = MediaPickerType.IMAGE_AND_VIDEO,
                            onResult = { result ->
                                when (result) {
                                    is MediaPickerResult.Success -> {
                                        Timber.d("File selected: ${result.file.name}, type: ${result.mediaType}")
                                        viewModel.setFile(result.file, result.mediaType)
                                    }
                                    is MediaPickerResult.Error -> {
                                        Timber.e("Media picker error: ${result.message}")
                                        // handled by viewModel
                                    }
                                    is MediaPickerResult.Cancelled -> {
                                        Timber.d("Media picker cancelled")
                                        // no-op
                                    }
                                }
                            },
                            showCamera = true,      // ── Step 3.4: Camera enabled ──
                            showGallery = true,
                            showFilePicker = true
                        )
                    }

                    // Media preview (if file selected)
                    val selectedFile = uiState.selectedFile
                    val mediaType = uiState.mediaType
                    if (selectedFile != null && mediaType != null) {
                        MediaPreviewForVerify(
                            file = selectedFile,
                            mediaType = mediaType,
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
                    val verdict = uiState.verdict
                    if (uiState.isVerified && verdict != null) {
                        VerdictCard(
                            verdict = verdict,
                            mode = VerdictCardMode.STANDARD,
                            modifier = Modifier.fillMaxWidth(),
                            onEvidenceClick = { evidenceId ->
                                onNavigateToVerdictDetail(evidenceId)
                            }
                        )
                    }

                    // ── Step 10: Evidence ID Search ──
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Search by Evidence ID",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            OutlinedTextField(
                                value = uiState.evidenceIdQuery,
                                onValueChange = viewModel::setEvidenceIdQuery,
                                label = { Text("Evidence ID") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("Enter evidence UUID") }
                            )
                            Button(
                                onClick = viewModel::searchEvidenceById,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isSearchingId && uiState.evidenceIdQuery.isNotBlank()
                            ) {
                                if (uiState.isSearchingId) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(if (uiState.isSearchingId) "Searching..." else "Search ID")
                            }
                        }
                    }

                    // ── Step 10: Matched evidence metadata (from upload verify) ──
                    val matchedRecord = uiState.matchedEvidenceRecord
                    if (matchedRecord != null) {
                        EvidenceMetadataCard(
                            title = "Matched Evidence Metadata",
                            record = matchedRecord
                        )
                    }

                    // ── Step 10: Searched evidence metadata (from ID search) ──
                    val searchedRecord = uiState.searchedEvidenceRecord
                    if (searchedRecord != null) {
                        EvidenceMetadataCard(
                            title = "Evidence Metadata",
                            record = searchedRecord
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
            .clip(RoundedCornerShape(12.dp)),
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
                        imageVector = if (mediaType == DmvpConstants.MEDIA_TYPE_IMAGE)
                            Icons.Default.Image else Icons.Default.Videocam,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (mediaType == DmvpConstants.MEDIA_TYPE_IMAGE) "Image" else "Video",
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

            // Thumbnail placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (mediaType == DmvpConstants.MEDIA_TYPE_IMAGE)
                                Icons.Default.Image else Icons.Default.Videocam,
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
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                        onClick = onClear,
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
// Step 10: Evidence Metadata Card
// ================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EvidenceMetadataCard(
    title: String,
    record: com.dmvp.app.data.remote.EvidenceRecord
) {
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                MetadataRow("Evidence ID", record.evidenceId, clipboardManager)
                MetadataRow("Media type", record.mediaType, clipboardManager)
                MetadataRow("Signer device key", record.signerDeviceKeyId, clipboardManager)
                record.signerDeviceId?.let { MetadataRow("Signer device ID", it, clipboardManager) }
                record.signerTrustTier?.let { MetadataRow("Trust tier", it, clipboardManager) }
                MetadataRow("SHA-256", record.sha256Original, clipboardManager)
                record.canonicalMediaHash?.let { MetadataRow("Canonical hash", it, clipboardManager) }
                MetadataRow("Lifecycle", record.lifecycleState, clipboardManager)
                MetadataRow("Created at", record.createdAt, clipboardManager)
                MetadataRow("Updated at", record.updatedAt, clipboardManager)

                // Owner contact
                val contact = record.ownerContact
                if (contact != null) {
                    Divider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Text(
                        text = "Owner Contact",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    contact.name?.let { MetadataRow("Name", it, clipboardManager) }
                    contact.phone?.let { MetadataRow("Phone", it, clipboardManager) }
                    contact.address?.let { MetadataRow("Address", it, clipboardManager) }
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = if (value.length > 40) value.take(20) + "..." + value.takeLast(10) else value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(value))
            },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy $label",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
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
