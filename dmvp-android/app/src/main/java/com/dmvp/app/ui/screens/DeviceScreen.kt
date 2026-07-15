/**
 * app/src/main/java/com/dmvp/app/ui/screens/DeviceScreen.kt
 *
 * DeviceScreen for DMVP v3.0 Android app.
 * Manages device keys: view current key, trust tier, attestation, and perform
 * rotation, revocation, and recovery operations.
 *
 * Features:
 *   - Display current device key info (ID, trust tier, hardware-backed status)
 *   - Display attestation summary
 *   - List all device keys with pagination
 *   - Rotate device key flow (with confirmation dialog)
 *   - Revoke device key flow (with confirmation dialog)
 *   - Recover device lineage flow
 *   - Loading and error states
 *   - Dark theme optimized
 *
 * Uses:
 *   - DeviceViewModel for state management
 *   - TrustTierBadge for trust tier display
 *   - LoadingOverlay for progress indication
 *   - AlertDialog for confirmations
 */

package com.dmvp.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmvp.app.data.model.DeviceTrustTier
import com.dmvp.app.ui.components.LoadingOverlay
import com.dmvp.app.ui.components.LoadingState
import com.dmvp.app.ui.components.TrustTierBadge
import com.dmvp.app.ui.components.TrustTierBadgeSize
import com.dmvp.app.ui.theme.*
import com.dmvp.app.ui.viewmodel.DeviceOperation
import com.dmvp.app.ui.viewmodel.DeviceViewModel
import timber.log.Timber

/**
 * DeviceScreen composable.
 *
 * @param onNavigateBack Callback to navigate back.
 * @param modifier Modifier for the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: DeviceViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    // Show confirmation dialog if requested
    if (uiState.showConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = {
                Text(
                    text = when (uiState.operation) {
                        DeviceOperation.REVOKING -> "Revoke Device Key"
                        DeviceOperation.ROTATING -> "Rotate Device Key"
                        DeviceOperation.RECOVERING -> "Recover Device Lineage"
                        else -> "Confirm"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = when (uiState.operation) {
                        DeviceOperation.REVOKING -> "Are you sure you want to revoke this device key? This cannot be undone."
                        DeviceOperation.ROTATING -> "Are you sure you want to rotate to a new device key?"
                        DeviceOperation.RECOVERING -> "Are you sure you want to recover device lineage? This will create a new key linked to the old one."
                        else -> "Are you sure?"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (uiState.operation) {
                            DeviceOperation.REVOKING -> viewModel.executeRevocation()
                            DeviceOperation.ROTATING -> viewModel.executeRotation()
                            DeviceOperation.RECOVERING -> viewModel.executeRecovery()
                            else -> viewModel.dismissDialog()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (uiState.operation) {
                            DeviceOperation.REVOKING -> Error
                            else -> Warning
                        }
                    )
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialog) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Device Management",
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
                    // Refresh button
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
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
                uiState.operation == DeviceOperation.ROTATING -> "Rotating device key..."
                uiState.operation == DeviceOperation.REVOKING -> "Revoking device key..."
                uiState.operation == DeviceOperation.RECOVERING -> "Recovering device lineage..."
                uiState.isLoading -> "Loading..."
                else -> null
            },
            progress = uiState.progress,
            state = when {
                uiState.rotationResult != null || uiState.recoveryResult != null -> LoadingState.SUCCESS
                uiState.error != null -> LoadingState.ERROR
                else -> LoadingState.LOADING
            },
            showCancelButton = true,
            onCancel = {
                viewModel.resetOperation()
            },
            onDismiss = {
                viewModel.clearError()
                viewModel.clearSuccess()
                viewModel.resetOperation()
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
                    val currentError = uiState.error
                    if (currentError != null) {
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
                                    text = currentError,
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

                    // Success message
                    val currentSuccessMessage = uiState.successMessage
                    if (currentSuccessMessage != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Success.copy(alpha = 0.1f)
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
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = Success,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = currentSuccessMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Success,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = viewModel::clearSuccess,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = Success,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Current device info
                    CurrentDeviceInfo(
                        deviceKeyId = uiState.currentDeviceKeyId,
                        trustTier = uiState.currentTrustTier,
                        isHardwareBacked = uiState.isHardwareBacked,
                        attestationAvailable = uiState.attestationAvailable,
                        attestationSummary = uiState.attestationSummary,
                        onRotate = { viewModel.startRotation() },
                        onRevoke = { viewModel.startRevocation() },
                        onRecover = { viewModel.startRecovery() }
                    )

                    // Device list (if available)
                    if (uiState.deviceList.isNotEmpty()) {
                        Divider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Text(
                            text = "All Device Keys (${uiState.deviceListTotal})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        // ── Step 7.5 Fix: LazyColumn → Column + forEach ──
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.deviceList.forEach { deviceKey ->
                                DeviceListItem(
                                    deviceKey = deviceKey,
                                    isCurrent = deviceKey.deviceKeyId == uiState.currentDeviceKeyId,
                                    onClick = {
                                        viewModel.selectDeviceKey(deviceKey.deviceKeyId)
                                    }
                                )
                            }
                        }
                    } else if (!uiState.isLoading) {
                        Text(
                            text = "No device keys found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    // Recovery flow (expanded)
                    if (uiState.operation == DeviceOperation.RECOVERING) {
                        RecoveryFlow(
                            oldDeviceKeyId = uiState.recoveryOldDeviceKeyId,
                            onOldKeyIdChange = viewModel::setRecoveryOldDeviceKeyId,
                            quorum = uiState.recoveryQuorum,
                            onQuorumChange = viewModel::setRecoveryQuorum,
                            onRecover = { viewModel.showConfirmation() },
                            onCancel = viewModel::resetOperation,
                            isLoading = uiState.isLoading
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        )
    }
}

/**
 * Current device info card.
 */
@Composable
private fun CurrentDeviceInfo(
    deviceKeyId: String?,
    trustTier: String?,
    isHardwareBacked: Boolean,
    attestationAvailable: Boolean,
    attestationSummary: com.dmvp.app.data.model.AttestationSummary?,
    onRotate: () -> Unit,
    onRevoke: () -> Unit,
    onRecover: () -> Unit
) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Device",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (deviceKeyId != null) {
                    TrustTierBadge(
                        trustTier = if (trustTier != null) DeviceTrustTier.valueOf(trustTier) else DeviceTrustTier.TIER_C,
                        size = TrustTierBadgeSize.SMALL
                    )
                }
            }

            // Device ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "ID:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = deviceKeyId ?: "None",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Hardware-backed status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Security:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = if (isHardwareBacked) "Hardware-backed ✅" else "Software-backed",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isHardwareBacked) Success else Warning
                )
            }

            // Attestation status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Attestation:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = if (attestationAvailable) "Available" else "Unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (attestationAvailable) Success else Error
                )
            }

            // Attestation summary (if available)
            if (attestationSummary != null) {
                Divider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Text(
                    text = "Attestation Details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                attestationSummary.extra?.entries?.take(2)?.forEach { (key, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "$key:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Text(
                            text = value.toString().take(30),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Action buttons
            if (deviceKeyId != null) {
                Divider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRotate,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Warning,
                            contentColor = Color.Black
                        ),
                        enabled = deviceKeyId != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.CompareArrows,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rotate")
                    }
                    Button(
                        onClick = onRevoke,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Error,
                            contentColor = Color.White
                        ),
                        enabled = deviceKeyId != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Revoke")
                    }
                }
                Button(
                    onClick = onRecover,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanBright,
                        contentColor = DeepPurple900
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Recover Lineage")
                }
            } else {
                Text(
                    text = "No device key registered.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Error,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Device list item.
 */
@Composable
private fun DeviceListItem(
    deviceKey: com.dmvp.app.data.model.DeviceKey,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
            }
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
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = deviceKey.deviceKeyId.take(16) + "...",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isCurrent) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Success.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Current",
                                style = MaterialTheme.typography.labelSmall,
                                color = Success,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Tier: ${deviceKey.trustTier.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "State: ${deviceKey.lifecycleState.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}

/**
 * Recovery flow input.
 */
@Composable
private fun RecoveryFlow(
    oldDeviceKeyId: String,
    onOldKeyIdChange: (String) -> Unit,
    quorum: String,
    onQuorumChange: (String) -> Unit,
    onRecover: () -> Unit,
    onCancel: () -> Unit,
    isLoading: Boolean
) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Recover Device Lineage",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Enter the old device key ID to recover from. A new key will be created linked to the old one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            OutlinedTextField(
                value = oldDeviceKeyId,
                onValueChange = onOldKeyIdChange,
                label = { Text("Old Device Key ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Enter device key ID") }
            )

            OutlinedTextField(
                value = quorum,
                onValueChange = onQuorumChange,
                label = { Text("Recovery Quorum (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Optional quorum proof") }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onRecover,
                    modifier = Modifier.weight(1f),
                    enabled = oldDeviceKeyId.isNotEmpty() && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanBright,
                        contentColor = DeepPurple900
                    )
                ) {
                    Text("Recover")
                }
            }
        }
    }
}

// ================================
// Preview
// ================================

@Preview(showBackground = true, backgroundColor = 0xFF1A0033)
@Composable
private fun DeviceScreenPreview() {
    DMVPTheme {
        DeviceScreen(
            onNavigateBack = {}
        )
    }
}
