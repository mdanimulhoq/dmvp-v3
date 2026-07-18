/**
 * app/src/main/java/com/dmvp/app/ui/screens/EvidenceDetailScreen.kt
 *
 * Full metadata view for a single registered evidence record.
 * Opened from Verify -> Matched evidence tap, Compare -> View full details,
 * and Search -> result tap.
 */

package com.dmvp.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmvp.app.ui.theme.Error
import com.dmvp.app.ui.viewmodel.EvidenceDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceDetailScreen(
    evidenceId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: EvidenceDetailViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(evidenceId) {
        viewModel.load(evidenceId)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Evidence details", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.error != null -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Error.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.error ?: "Failed",
                                color = Error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                uiState.record != null -> {
                    val r = uiState.record!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Registered evidence",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row {
                                Icon(Icons.Default.Fingerprint, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = r.evidenceId,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Divider(modifier = Modifier.padding(vertical = 12.dp))

                            Field("Media type", r.mediaType)
                            Field("Signer device key", r.signerDeviceKeyId)
                            Field("SHA-256 (original)", r.sha256Original)
                            r.canonicalMediaHash?.let { Field("Canonical hash", it) }
                            Field("Lifecycle state", r.lifecycleState)
                            Field("Created at", r.createdAt)
                            Field("Updated at", r.updatedAt)
                            r.ownerAccountId?.let { Field("Owner account", it) }

                            // Timestamp references (batching, TSA, etc.)
                            val ts = r.timestampReferences
                            if (!ts.isNullOrEmpty()) {
                                Divider(modifier = Modifier.padding(vertical = 12.dp))
                                Text(
                                    text = "Timestamp references",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                ts.forEach { (k, v) ->
                                    Field(k, v.toString())
                                }
                            }

                            // Fingerprint algorithm versions (defensive: type
                            // may vary across policy versions; toString is
                            // always safe).
                            Divider(modifier = Modifier.padding(vertical = 12.dp))
                            Text(
                                text = "Fingerprint algorithms",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Field("Versions", r.fingerprintAlgorithmVersions.toString())
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
