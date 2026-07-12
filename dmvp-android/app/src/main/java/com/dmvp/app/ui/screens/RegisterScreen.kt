/**
 * app/src/main/java/com/dmvp/app/ui/screens/RegisterScreen.kt
 *
 * RegisterScreen for DMVP v3.0 Android app.
 * Handles the complete registration flow: review CEE, sign, submit to registry.
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmvp.app.data.model.DeviceTrustTier
import com.dmvp.app.ui.components.CEEPreview
import com.dmvp.app.ui.components.LoadingOverlay
import com.dmvp.app.ui.components.LoadingState
import com.dmvp.app.ui.components.TrustTierBadge
import com.dmvp.app.ui.components.TrustTierBadgeSize
import com.dmvp.app.ui.theme.*
import com.dmvp.app.ui.viewmodel.RegisterViewModel
import com.dmvp.app.ui.viewmodel.getStepDescription
import com.dmvp.app.ui.viewmodel.isReadyToSubmit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEvidenceDetail: (String) -> Unit,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: RegisterViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isRegistered) {
        val registrationResult = uiState.registrationResult
        if (uiState.isRegistered && registrationResult != null) {
            onNavigateToEvidenceDetail(registrationResult.evidenceId)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Register Evidence",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.isLoading) {
                                // Optionally cancel loading
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
                    TrustTierBadge(
                        trustTier = DeviceTrustTier.TIER_A,
                        size = TrustTierBadgeSize.SMALL,
                        modifier = Modifier.padding(end = 8.dp)
                    )
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
                uiState.currentStep == com.dmvp.app.ui.viewmodel.RegistrationStep.SUBMITTING -> "Submitting registration..."
                uiState.currentStep == com.dmvp.app.ui.viewmodel.RegistrationStep.SIGNING -> "Signing evidence..."
                uiState.currentStep == com.dmvp.app.ui.viewmodel.RegistrationStep.BUILDING_CEE -> "Building CEE..."
                uiState.isLoading -> "Processing..."
                else -> null
            },
            progress = uiState.progress,
            state = when {
                uiState.isRegistered -> LoadingState.SUCCESS
                uiState.currentStep == com.dmvp.app.ui.viewmodel.RegistrationStep.ERROR -> LoadingState.ERROR
                else -> LoadingState.LOADING
            },
            showCancelButton = !uiState.isRegistered,
            onCancel = {
                viewModel.reset()
                onNavigateBack()
            },
            onDismiss = {
                viewModel.clearError()
                if (uiState.isRegistered) {
                    onNavigateToHome()
                }
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
                    val error = uiState.error
                    if (error != null) {
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
                                    text = error,
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

                    val deviceKeyId = uiState.deviceKeyId
                    if (deviceKeyId != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Device Info",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Key ID:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = deviceKeyId.take(20) + "...",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                val trustTier = uiState.trustTier
                                if (trustTier != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Trust Tier:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                        TrustTierBadge(
                                            trustTier = DeviceTrustTier.valueOf(trustTier),
                                            size = TrustTierBadgeSize.SMALL
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val cee = uiState.cee
                    if (cee != null) {
                        CEEPreview(
                            cee = cee,
                            modifier = Modifier.fillMaxWidth(),
                            showSignature = true,
                            onCopyClick = { field, value ->
                                // Copy to clipboard handled in CEEPreview
                            }
                        )
                    } else {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(40.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Building evidence envelope...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = viewModel::submitRegistration,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = uiState.isReadyToSubmit(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanBright,
                            contentColor = DeepPurple900
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Register Evidence",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = uiState.getStepDescription(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A0033)
@Composable
private fun RegisterScreenPreview() {
    DMVPTheme {
        RegisterScreen(
            onNavigateBack = {},
            onNavigateToEvidenceDetail = {},
            onNavigateToHome = {}
        )
    }
}
