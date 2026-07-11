/**
 * app/src/main/java/com/dmvp/app/ui/screens/HomeScreen.kt
 *
 * HomeScreen for DMVP v3.0 Android app.
 * Serves as the main dashboard with quick access to all features.
 *
 * Features:
 *   - App header with logo and device trust tier status
 *   - Quick action cards: Register, Verify, Search, Device
 *   - Recent activity / stats placeholder
 *   - Dark theme optimized with deep purple and cyan accent
 *
 * Navigation destinations:
 *   - Register: Navigate to capture/register flow
 *   - Verify: Navigate to verify flow
 *   - Search: Navigate to search flow
 *   - Device: Navigate to device management
 *
 * Uses:
 *   - DMVPRepository for device status
 *   - TrustTierBadge for displaying device trust tier
 *   - ViewModel for state management (optional, we'll use a simple state)
 */

package com.dmvp.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmvp.app.data.model.DeviceTrustTier
import com.dmvp.app.ui.components.TrustTierBadge
import com.dmvp.app.ui.components.TrustTierBadgeSize
import com.dmvp.app.ui.theme.*

/**
 * HomeScreen composable.
 *
 * @param onNavigateToCapture Callback to navigate to capture screen.
 * @param onNavigateToVerify Callback to navigate to verify screen.
 * @param onNavigateToSearch Callback to navigate to search screen.
 * @param onNavigateToDevice Callback to navigate to device management screen.
 * @param modifier Modifier for the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToCapture: () -> Unit,
    onNavigateToVerify: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToDevice: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Simulate device trust tier (in production, get from ViewModel/repository)
    val trustTier = DeviceTrustTier.TIER_A // placeholder

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "DMVP v3.0",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    // Device trust tier badge in top bar
                    TrustTierBadge(
                        trustTier = trustTier,
                        size = TrustTierBadgeSize.SMALL,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    IconButton(onClick = onNavigateToDevice) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Device Settings",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Welcome / Status
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Welcome to DMVP",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Decentralized Media Verification Protocol",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "Store proofs, not content.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Quick Action Grid
            item {
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(actionItems) { action ->
                        ActionCard(
                            icon = action.icon,
                            title = action.title,
                            description = action.description,
                            color = action.color,
                            onClick = when (action.id) {
                                "register" -> onNavigateToCapture
                                "verify" -> onNavigateToVerify
                                "search" -> onNavigateToSearch
                                "device" -> onNavigateToDevice
                                else -> {}
                            }
                        )
                    }
                }
            }

            // Recent Activity / Stats (placeholder)
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Placeholder stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(label = "Registered", value = "0")
                            StatItem(label = "Verified", value = "0")
                            StatItem(label = "Trust Tier", value = trustTier.getLabel())
                        }
                        Divider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Text(
                            text = "No recent activity to show.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // Footer
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "DMVP v3.0 • Zero Storage • Proof-Based",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Action card for quick actions.
 */
@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * Stat item for activity stats.
 */
@Composable
private fun StatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

/**
 * Action item data class.
 */
private data class ActionItem(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val description: String,
    val color: Color
)

/**
 * List of quick action items.
 */
private val actionItems = listOf(
    ActionItem(
        id = "register",
        icon = Icons.Default.AddPhotoAlternate,
        title = "Register",
        description = "Capture or select media to register",
        color = CyanBright
    ),
    ActionItem(
        id = "verify",
        icon = Icons.Default.Verified,
        title = "Verify",
        description = "Verify media against registry",
        color = Success
    ),
    ActionItem(
        id = "search",
        icon = Icons.Default.Search,
        title = "Search",
        description = "Search for evidence by hash or similarity",
        color = Info
    ),
    ActionItem(
        id = "device",
        icon = Icons.Default.Devices,
        title = "Device",
        description = "Manage device keys and trust",
        color = Warning
    )
)

// ================================
// Preview
// ================================

@Preview(showBackground = true, backgroundColor = 0xFF1A0033)
@Composable
private fun HomeScreenPreview() {
    DMVPTheme {
        HomeScreen(
            onNavigateToCapture = {},
            onNavigateToVerify = {},
            onNavigateToSearch = {},
            onNavigateToDevice = {}
        )
    }
}
