/**
 * app/src/main/java/com/dmvp/app/ui/screens/CompareScreen.kt
 *
 * Two-media comparison flow for DMVP v3.
 *
 * Step 1: user picks a REFERENCE media (previously registered).
 * Step 2: user picks a CANDIDATE media (new file to check).
 * Step 3: app shows whether they are the same and, if so, exposes the
 *         reference's registered provenance metadata (device info, signer
 *         key id, timestamps, trust tier).
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmvp.app.ui.components.LoadingOverlay
import com.dmvp.app.ui.components.LoadingState
import com.dmvp.app.ui.components.MediaPicker
import com.dmvp.app.ui.components.MediaPickerResult
import com.dmvp.app.ui.components.MediaPickerType
import com.dmvp.app.ui.theme.Error
import com.dmvp.app.ui.theme.Success
import com.dmvp.app.ui.theme.Warning
import com.dmvp.app.ui.viewmodel.CompareOutcome
import com.dmvp.app.ui.viewmodel.CompareStep
import com.dmvp.app.ui.viewmodel.CompareViewModel
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEvidenceDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: CompareViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Compare Media",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!uiState.isLoading) onNavigateBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LoadingOverlay(
            isLoading = uiState.isLoading,
            message = when (uiState.step) {
                CompareStep.PICK_REFERENCE -> "Looking up reference in registry..."
                CompareStep.PICK_CANDIDATE -> "Comparing with reference..."
                CompareStep.RESULT -> "Working..."
            },
            progress = uiState.progress,
            state = when {
                uiState.error != null && !uiState.isLoading -> LoadingState.ERROR
                uiState.step == CompareStep.RESULT -> LoadingState.SUCCESS
                else -> LoadingState.LOADING
            },
            showCancelButton = false,
            onCancel = { viewModel.clearReference() },
            onDismiss = { viewModel.clearError() },
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IntroCard(uiState.step)

                    val err = uiState.error
                    if (err != null) {
                        ErrorBanner(err) { viewModel.clearError() }
                    }

                    // STEP 1: Reference picker / preview
                    ReferenceSection(
                        state = uiState,
                        onPickReference = { file, mediaType ->
                            Timber.d("Compare reference: ${file.name} / $mediaType")
                            viewModel.setReference(file, mediaType)
                        },
                        onClearReference = { viewModel.clearReference() }
                    )

                    // STEP 2: Candidate picker (only once reference is set)
                    if (uiState.referenceFile != null &&
                        uiState.referenceSha256 != null &&
                        uiState.step != CompareStep.PICK_REFERENCE
                    ) {
                        CandidateSection(
                            state = uiState,
                            onPickCandidate = { file, mediaType ->
                                Timber.d("Compare candidate: ${file.name} / $mediaType")
                                viewModel.setCandidate(file, mediaType)
                            },
                            onClearCandidate = { viewModel.clearCandidate() }
                        )
                    }

                    // STEP 3: Result
                    if (uiState.step == CompareStep.RESULT && uiState.outcome != null) {
                        CompareResultCard(
                            outcome = uiState.outcome!!,
                            similarityScore = uiState.similarityScore,
                            record = uiState.referenceRecord,
                            onOpenEvidenceDetail = { evidenceId ->
                                onNavigateToEvidenceDetail(evidenceId)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        )
    }
}

@Composable
private fun IntroCard(step: CompareStep) {
    val (stepLabel, description) = when (step) {
        CompareStep.PICK_REFERENCE ->
            "Step 1 of 2" to "Pick the ORIGINAL registered media (reference)."
        CompareStep.PICK_CANDIDATE ->
            "Step 2 of 2" to "Pick a NEW file to check whether it matches the reference."
        CompareStep.RESULT ->
            "Result" to "Comparison finished. See match details below."
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stepLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Error.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Error)
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Error,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Error)
            }
        }
    }
}

@Composable
private fun ReferenceSection(
    state: com.dmvp.app.ui.viewmodel.CompareUiState,
    onPickReference: (java.io.File, String) -> Unit,
    onClearReference: () -> Unit
) {
    Text(
        text = "Reference (registered media)",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
    val refFile = state.referenceFile
    if (refFile == null) {
        MediaPicker(
            mediaType = MediaPickerType.IMAGE_AND_VIDEO,
            onResult = { r ->
                when (r) {
                    is MediaPickerResult.Success -> onPickReference(r.file, r.mediaType)
                    is MediaPickerResult.Error -> Timber.e("Reference picker error: ${r.message}")
                    is MediaPickerResult.Cancelled -> Unit
                }
            },
            showCamera = false,
            showGallery = true,
            showFilePicker = true
        )
    } else {
        SelectedFileRow(
            title = refFile.name,
            subtitle = state.referenceMediaType ?: "unknown",
            hash = state.referenceSha256,
            badge = if (state.referenceRecord != null) "Registered" else "Not in registry",
            badgeColor = if (state.referenceRecord != null) Success else Warning,
            onClear = onClearReference
        )
    }
}

@Composable
private fun CandidateSection(
    state: com.dmvp.app.ui.viewmodel.CompareUiState,
    onPickCandidate: (java.io.File, String) -> Unit,
    onClearCandidate: () -> Unit
) {
    Text(
        text = "Candidate (media to check)",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
    val candFile = state.candidateFile
    if (candFile == null) {
        MediaPicker(
            mediaType = MediaPickerType.IMAGE_AND_VIDEO,
            onResult = { r ->
                when (r) {
                    is MediaPickerResult.Success -> onPickCandidate(r.file, r.mediaType)
                    is MediaPickerResult.Error -> Timber.e("Candidate picker error: ${r.message}")
                    is MediaPickerResult.Cancelled -> Unit
                }
            },
            showCamera = true,
            showGallery = true,
            showFilePicker = true
        )
    } else {
        SelectedFileRow(
            title = candFile.name,
            subtitle = state.candidateMediaType ?: "unknown",
            hash = state.candidateSha256,
            badge = null,
            badgeColor = MaterialTheme.colorScheme.primary,
            onClear = onClearCandidate
        )
    }
}

@Composable
private fun SelectedFileRow(
    title: String,
    subtitle: String,
    hash: String?,
    badge: String?,
    badgeColor: androidx.compose.ui.graphics.Color,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                if (badge != null) {
                    AssistChip(
                        onClick = {},
                        label = { Text(badge, style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = badgeColor
                        )
                    )
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "Clear")
                }
            }
            if (!hash.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "sha256: ${hash.take(24)}…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun CompareResultCard(
    outcome: CompareOutcome,
    similarityScore: Double?,
    record: com.dmvp.app.data.remote.EvidenceRecord?,
    onOpenEvidenceDetail: (String) -> Unit
) {
    val (title, color, description) = when (outcome) {
        CompareOutcome.EXACT_MATCH -> Triple(
            "Exact match",
            Success,
            "Both files are byte-for-byte identical. This is the same media."
        )
        CompareOutcome.CANONICAL_MATCH -> Triple(
            "Canonical match",
            Success,
            "The media content matches (after re-encode / metadata strip). Likely the same photo/video."
        )
        CompareOutcome.SIMILAR_MATCH -> Triple(
            "Similar match",
            Warning,
            "Perceptual fingerprint matched. Likely a derivative of the reference."
        )
        CompareOutcome.NO_MATCH -> Triple(
            "No match",
            Error,
            "The candidate does not match the reference registered in the registry."
        )
        CompareOutcome.REFERENCE_NOT_REGISTERED -> Triple(
            "Reference not registered",
            Warning,
            "Register the reference first, then you'll see full provenance metadata."
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (outcome) {
                        CompareOutcome.EXACT_MATCH,
                        CompareOutcome.CANONICAL_MATCH -> Icons.Default.CheckCircle
                        CompareOutcome.SIMILAR_MATCH -> Icons.Default.CompareArrows
                        CompareOutcome.NO_MATCH -> Icons.Default.Cancel
                        CompareOutcome.REFERENCE_NOT_REGISTERED -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = color
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                if (similarityScore != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${(similarityScore * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            // Provenance metadata (only meaningful for a match)
            if (record != null && outcome != CompareOutcome.NO_MATCH &&
                outcome != CompareOutcome.REFERENCE_NOT_REGISTERED
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Registered provenance",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                MetaRow("Evidence ID", record.evidenceId)
                MetaRow("Media type", record.mediaType)
                MetaRow("Signer device", record.signerDeviceKeyId)
                MetaRow("Registered at", record.createdAt)
                MetaRow("Lifecycle", record.lifecycleState)
                if (record.canonicalMediaHash != null) {
                    MetaRow(
                        "Canonical hash",
                        record.canonicalMediaHash.take(24) + "…"
                    )
                }
                MetaRow("SHA-256", record.sha256Original.take(24) + "…")
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { onOpenEvidenceDetail(record.evidenceId) }) {
                    Text("View full evidence details")
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
