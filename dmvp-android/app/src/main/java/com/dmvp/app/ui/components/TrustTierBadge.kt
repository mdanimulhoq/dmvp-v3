/**
 * app/src/main/java/com/dmvp/app/ui/components/TrustTierBadge.kt
 *
 * TrustTierBadge UI component for DMVP v3.0 Android app.
 * Displays device trust tier with appropriate colors, icons, and labels.
 *
 * Supports four trust tiers:
 *   - TIER_A: Hardware-backed key + valid attestation (green)
 *   - TIER_B: Hardware-backed key, attestation unavailable/degraded (amber)
 *   - TIER_C: Software-backed key or desktop secure store (orange)
 *   - TIER_D: Revoked or untrusted device (red)
 *
 * Features:
 *   - Color-coded badge with tier label
 *   - Optional icon (shield, check, warning, cross)
 *   - Tooltip/description on long press (optional)
 *   - Compact and standard sizes
 *   - Dark theme optimized
 *
 * Used in:
 *   - DeviceScreen: showing current device trust tier
 *   - VerdictCard: showing evidence quality derived from trust tier
 *   - HomeScreen: showing device status
 *   - EvidenceDetail: showing trust tier of signing device
 */

package com.dmvp.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmvp.app.data.model.DeviceTrustTier
import com.dmvp.app.ui.theme.*

/**
 * Size variant for the trust tier badge.
 */
enum class TrustTierBadgeSize {
    SMALL,      // Compact, used in lists or compact views
    MEDIUM,     // Standard size, used in most places
    LARGE       // Large with more detail, used in detail screens
}

/**
 * Holds the four dimension values for a badge size variant.
 * A plain data class supports component1..component4 destructuring,
 * unlike chained `to` (Pair) which only supports two components.
 */
private data class BadgeDimensions(
    val paddingHorizontal: Dp,
    val paddingVertical: Dp,
    val iconSize: Dp,
    val fontSize: TextUnit
)

/**
 * TrustTierBadge composable.
 *
 * @param trustTier The device trust tier to display.
 * @param size Size variant (small, medium, large).
 * @param showLabel Whether to show the tier label (default: true).
 * @param showIcon Whether to show the icon (default: true).
 * @param modifier Modifier for the badge container.
 * @param onClick Optional click handler.
 */
@Composable
fun TrustTierBadge(
    trustTier: DeviceTrustTier,
    size: TrustTierBadgeSize = TrustTierBadgeSize.MEDIUM,
    showLabel: Boolean = true,
    showIcon: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val tierInfo = getTierInfo(trustTier)
    val backgroundColor = tierInfo.color.copy(alpha = 0.15f)
    val borderColor = tierInfo.color.copy(alpha = 0.4f)
    val textColor = tierInfo.color

    // Dimensions based on size
    val dimensions = when (size) {
        TrustTierBadgeSize.SMALL -> BadgeDimensions(8.dp, 4.dp, 16.dp, 10.sp)
        TrustTierBadgeSize.MEDIUM -> BadgeDimensions(12.dp, 6.dp, 20.dp, 12.sp)
        TrustTierBadgeSize.LARGE -> BadgeDimensions(16.dp, 8.dp, 24.dp, 14.sp)
    }
    val (paddingHorizontal, paddingVertical, iconSize, fontSize) = dimensions

    val clickableModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Surface(
        modifier = clickableModifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = paddingHorizontal, vertical = paddingVertical),
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showIcon) {
                Icon(
                    imageVector = tierInfo.icon,
                    contentDescription = tierInfo.label,
                    tint = textColor,
                    modifier = Modifier.size(iconSize)
                )
            }
            if (showLabel) {
                Text(
                    text = tierInfo.label,
                    color = textColor,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * TrustTierBadge with a more detailed tooltip/description.
 * Shows the tier, label, and a brief description on hover/long press.
 */
@Composable
fun TrustTierBadgeWithTooltip(
    trustTier: DeviceTrustTier,
    size: TrustTierBadgeSize = TrustTierBadgeSize.MEDIUM,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val tierInfo = getTierInfo(trustTier)

    Box(
        modifier = modifier
    ) {
        // In a real implementation, you might use a TooltipBox or long-press state.
        // For simplicity, we just show the badge with a description below it.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TrustTierBadge(
                trustTier = trustTier,
                size = size,
                onClick = onClick
            )
            Text(
                text = tierInfo.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Get tier information: label, color, icon, description.
 */
data class TrustTierInfo(
    val label: String,
    val color: Color,
    val icon: ImageVector,
    val description: String
)

fun getTierInfo(trustTier: DeviceTrustTier): TrustTierInfo {
    return when (trustTier) {
        DeviceTrustTier.TIER_A -> TrustTierInfo(
            label = "Tier A",
            color = TierABadge,
            icon = Icons.Default.Verified,
            description = "Hardware-backed key with valid attestation"
        )
        DeviceTrustTier.TIER_B -> TrustTierInfo(
            label = "Tier B",
            color = TierBBadge,
            icon = Icons.Default.Shield,
            description = "Hardware-backed key, attestation degraded"
        )
        DeviceTrustTier.TIER_C -> TrustTierInfo(
            label = "Tier C",
            color = TierCBadge,
            icon = Icons.Default.Info,
            description = "Software-backed key or desktop secure store"
        )
        DeviceTrustTier.TIER_D -> TrustTierInfo(
            label = "Tier D",
            color = TierDBadge,
            icon = Icons.Default.Warning,
            description = "Revoked or untrusted device"
        )
    }
}

/**
 * Convenience extension to get color for UI elements.
 */
fun DeviceTrustTier.getColor(): Color {
    return getTierInfo(this).color
}

/**
 * Convenience extension to get label for display.
 */
fun DeviceTrustTier.getLabel(): String {
    return getTierInfo(this).label
}

/**
 * Check if a trust tier is considered "secure" (Tier A or B).
 */
fun DeviceTrustTier.isSecure(): Boolean {
    return this == DeviceTrustTier.TIER_A || this == DeviceTrustTier.TIER_B
}

/**
 * Check if a trust tier is "high assurance" (Tier A).
 */
fun DeviceTrustTier.isHighAssurance(): Boolean {
    return this == DeviceTrustTier.TIER_A
}

// ================================
// Previews
// ================================

@Preview(showBackground = true, backgroundColor = 0xFF1A0033)
@Composable
private fun TrustTierBadgePreview() {
    DmvpTheme {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Trust Tier Badges",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // All tiers
                DeviceTrustTier.values().forEach { tier ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrustTierBadge(
                            trustTier = tier,
                            size = TrustTierBadgeSize.MEDIUM
                        )
                        TrustTierBadge(
                            trustTier = tier,
                            size = TrustTierBadgeSize.SMALL,
                            showLabel = false
                        )
                        TrustTierBadgeWithTooltip(
                            trustTier = tier,
                            size = TrustTierBadgeSize.SMALL
                        )
                    }
                }
            }
        }
    }
}
