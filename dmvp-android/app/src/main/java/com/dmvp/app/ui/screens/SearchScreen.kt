/**
 * app/src/main/java/com/dmvp/app/ui/screens/SearchScreen.kt
 *
 * SearchScreen for DMVP v3.0 Android app.
 * Allows searching for evidence by SHA-256 hash or by uploading a file for similarity search.
 */

package com.dmvp.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmvp.app.data.model.MatchedEvidence
import com.dmvp.app.ui.components.LoadingOverlay
import com.dmvp.app.ui.components.LoadingState
import com.dmvp.app.ui.components.MediaPicker
import com.dmvp.app.ui.components.MediaPickerResult
import com.dmvp.app.ui.components.MediaPickerType
import com.dmvp.app.ui.theme.CyanBright
import com.dmvp.app.ui.theme.DMVPTheme
import com.dmvp.app.ui.theme.DeepPurple900
import com.dmvp.app.ui.theme.Error
import com.dmvp.app.ui.theme.Success
import com.dmvp.app.ui.theme.Warning
import com.dmvp.app.ui.viewmodel.SearchMode
import com.dmvp.app.ui.viewmodel.SearchViewModel
import com.dmvp.app.ui.viewmodel.getBestMatchDescription
import com.dmvp.app.ui.viewmodel.getFormattedScore
import com.dmvp.app.ui.viewmodel.getMatchTypeColor
import com.dmvp.app.ui.viewmodel.getMatchTypeLabel
import com.dmvp.app.utils.DmvpConstants
import com.dmvp.app.utils.isValidSha256

/**
 * SearchScreen composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEvidenceDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: SearchViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    // Handle navigation when a match is selected
    LaunchedEffect(uiState.selectedEvidenceId) {
        val id = uiState.selectedEvidenceId
        if (!id.isNullOrEmpty()) {
            onNavigateToEvidenceDetail(id)
            // Clear the selection after navigation
            viewModel.selectMatch("")
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Search Evidence",
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
                actions = {
                    if (uiState.hasSearched && uiState.matchedEvidence.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearResults) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear results",
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
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
            message = "Searching...",
            progress = uiState.progress,
            state = when {
                uiState.hasSearched && uiState.matchedEvidence.isNotEmpty() -> LoadingState.SUCCESS
                uiState.error != null -> LoadingState.ERROR
                else -> LoadingState.LOADING
            },
            showCancelButton = true,
            onCancel = {
                viewModel.clearResults()
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

                    // Search mode selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SearchMode.values().forEach { mode ->
                            FilterChip(
                                selected = uiState.searchMode == mode,
                                onClick = {
                                    viewModel.setSearchMode(mode)
                                    viewModel.clearResults()
                                },
                                label = {
                                    Text(
                                        text = when (mode) {
                                            SearchMode.EXACT -> "Exact Hash"
                                            SearchMode.SIMILARITY -> "Similarity"
                                            SearchMode.METADATA -> "Metadata"
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                leadingIcon = if (uiState.searchMode == mode) {
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

                    // Search input
                    when (uiState.searchMode) {
                        SearchMode.EXACT -> {
                            ExactSearchInput(
                                query = uiState.querySha256,
                                onQueryChange = viewModel::setQuerySha256,
                                onSearch = {
                                    if (uiState.querySha256.isValidSha256()) {
                                        viewModel.performSearch()
                                    } else {
                                        viewModel.setQuerySha256(uiState.querySha256)
                                    }
                                },
                                isLoading = uiState.isLoading,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        SearchMode.SIMILARITY -> {
                            SimilaritySearchInput(
                                selectedFile = uiState.selectedFile,
                                mediaType = uiState.mediaType,
                                onFileSelected = { file, mediaType ->
                                    viewModel.setSearchFile(file, mediaType)
                                },
                                onSearch = {
                                    if (uiState.selectedFile != null && uiState.fingerprint != null) {
                                        viewModel.performSearch()
                                    }
                                },
                                isLoading = uiState.isLoading,
                                isReady = uiState.selectedFile != null && uiState.fingerprint != null,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        SearchMode.METADATA -> {
                            Text(
                                text = "Metadata search not implemented in MVP.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    // Results summary
                    if (uiState.hasSearched) {
                        if (uiState.matchedEvidence.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No matching evidence found.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Found ${uiState.totalMatches} match${if (uiState.totalMatches > 1) "es" else ""}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Best match: ${uiState.getBestMatchDescription()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Results list
                    if (uiState.hasSearched && uiState.matchedEvidence.isNotEmpty()) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.heightIn(max = 600.dp)
                        ) {
                            items(uiState.matchedEvidence) { match ->
                                SearchResultItem(
                                    match = match,
                                    onClick = {
                                        viewModel.selectMatch(match.evidenceId)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        )
    }
}
/**
 * Exact search input with text field and search button.
 */
@Composable
private fun ExactSearchInput(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("SHA-256 Hash") },
            placeholder = { Text("Enter 64-char hex hash") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            isError = query.isNotEmpty() && !query.isValidSha256(),
            supportingText = {
                if (query.isNotEmpty() && !query.isValidSha256()) {
                    Text("Must be 64 hex characters", color = Error)
                }
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        )
        Button(
            onClick = onSearch,
            enabled = query.isValidSha256() && !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanBright,
                contentColor = DeepPurple900
            )
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Similarity search input with file picker and search button.
 */
@Composable
private fun SimilaritySearchInput(
    selectedFile: java.io.File?,
    mediaType: String?,
    onFileSelected: (java.io.File, String) -> Unit,
    onSearch: () -> Unit,
    isLoading: Boolean,
    isReady: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MediaPicker(
            mediaType = MediaPickerType.IMAGE_AND_VIDEO,
            onResult = { result ->
                when (result) {
                    is MediaPickerResult.Success -> {
                        onFileSelected(result.file, result.mediaType)
                    }
                    is MediaPickerResult.Error -> {
                        // handled by viewModel error
                    }
                    is MediaPickerResult.Cancelled -> {
                        // no-op
                    }
                }
            },
            showCamera = false,
            showGallery = true,
            showFilePicker = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Selected file info
        if (selectedFile != null && mediaType != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
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
                            text = selectedFile.name.take(20) + "...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = selectedFile.getReadableSize(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    IconButton(
                        onClick = { onFileSelected(selectedFile, mediaType) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Button(
            onClick = onSearch,
            enabled = isReady && !isLoading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanBright,
                contentColor = DeepPurple900
            )
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Search by Similarity")
        }
    }
}
/**
 * Single search result item.
 */
@Composable
private fun SearchResultItem(
    match: MatchedEvidence,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (match.matchType) {
                            "exact" -> Icons.Default.CheckCircle
                            "canonical" -> Icons.Default.CheckCircle
                            "similarity" -> Icons.Default.CompareArrows
                            else -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = Color(match.getMatchTypeColor()),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = match.getMatchTypeLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(match.getMatchTypeColor()),
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "ID: ${match.evidenceId.take(12)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (match.sha256 != null) {
                    Text(
                        text = "Hash: ${match.sha256.take(12)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
            // Score
            val simScore = match.similarityScore
            if (simScore != null) {
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = match.getFormattedScore(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = when {
                            simScore >= 0.8 -> Success
                            simScore >= 0.5 -> Warning
                            else -> Error
                        }
                    )
                    Text(
                        text = "similarity",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            } else {
                Text(
                    text = "Exact",
                    style = MaterialTheme.typography.labelMedium,
                    color = Success
                )
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
private fun SearchScreenPreview() {
    DMVPTheme {
        SearchScreen(
            onNavigateBack = {},
            onNavigateToEvidenceDetail = {}
        )
    }
}
