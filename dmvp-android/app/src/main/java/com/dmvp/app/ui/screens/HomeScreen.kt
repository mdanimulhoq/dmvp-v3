/**
 * app/src/main/java/com/dmvp/app/ui/screens/HomeScreen.kt
 *
 * UDOVP V2 — Dashboard Screen (cyberpunk/terminal aesthetic)
 * Follows docs/ui-v2.html #s-dashboard design
 *
 * PR 3: Dashboard + Bottom Navigation
 */

package com.dmvp.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmvp.app.ui.components.*
import com.dmvp.app.ui.theme.*

@Composable
fun HomeScreen(
    onNavigateToCapture: () -> Unit,
    onNavigateToVerify: () -> Unit,
    onNavigateToCompare: () -> Unit,
    onNavigateToDevice: () -> Unit,
    onNavigateToAccount: () -> Unit = {},
    onNavigateToAssets: () -> Unit = {},
    onNavigateToClaims: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgBase)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 8.dp, bottom = 100.dp), // bottom padding for nav
    ) {
        // ═══════════════════════════════════════════════════════
        // Header — Logo + Account Button
        // ═══════════════════════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Logo
            Column {
                Text(
                    text = "UDOVP",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = CyanPrimary,
                    letterSpacing = (-0.5).sp,
                )
                Text(
                    text = "UNIVERSAL OWNERSHIP",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted,
                    letterSpacing = 3.sp,
                )
            }

            // Account button
            Row(
                modifier = Modifier
                    .clickable { onNavigateToAccount() }
                    .background(
                        CyanPrimary.copy(alpha = 0.06f),
                        androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
                    )
                    .then(
                        Modifier.border(
                            1.dp,
                            CyanPrimary.copy(alpha = 0.15f),
                            androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
                        )
                    )
                    .padding(start = 4.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Avatar circle
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                GradientAccent,
                            ),
                            androidx.compose.foundation.shape.CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "AH",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.Black,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Account",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
            }
        }

        // ═══════════════════════════════════════════════════════
        // Stats Grid (2x2)
        // ═══════════════════════════════════════════════════════
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DmvpStatBox(
                    value = "1,204",
                    label = "Total Assets",
                    modifier = Modifier.weight(1f),
                    accentColor = CyanPrimary,
                )
                DmvpStatBox(
                    value = "98.6%",
                    label = "Trust Score",
                    modifier = Modifier.weight(1f),
                    valueColor = CyanPrimary,
                    accentColor = PurplePrimary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DmvpStatBox(
                    value = "23",
                    label = "Pending Claims",
                    modifier = Modifier.weight(1f),
                    valueColor = AmberAccent,
                    accentColor = AmberAccent,
                )
                DmvpStatBox(
                    value = "7",
                    label = "Devices",
                    modifier = Modifier.weight(1f),
                    valueColor = PurplePrimary,
                    accentColor = PinkAccent,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════
        // Quick Actions (3-column grid)
        // ═══════════════════════════════════════════════════════
        DmvpSectionHeader(text = "Quick Actions")

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DmvpActionTile(
                    icon = "\uD83D\uDCE4",
                    label = "Register",
                    onClick = onNavigateToCapture,
                    modifier = Modifier.weight(1f),
                )
                DmvpActionTile(
                    icon = "\uD83D\uDEE1\uFE0F",
                    label = "Verify",
                    onClick = onNavigateToVerify,
                    modifier = Modifier.weight(1f),
                )
                DmvpActionTile(
                    icon = "\u2696\uFE0F",
                    label = "Compare",
                    onClick = onNavigateToCompare,
                    modifier = Modifier.weight(1f),
                )
            }
            // Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DmvpActionTile(
                    icon = "\uD83D\uDD0D",
                    label = "AI Search",
                    onClick = onNavigateToSearch,
                    modifier = Modifier.weight(1f),
                )
                DmvpActionTile(
                    icon = "\uD83D\uDCDC",
                    label = "Assets",
                    onClick = onNavigateToAssets,
                    modifier = Modifier.weight(1f),
                )
                DmvpActionTile(
                    icon = "\u26A1",
                    label = "Claims",
                    onClick = onNavigateToClaims,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════
        // Blockchain Anchors
        // ═══════════════════════════════════════════════════════
        DmvpSectionHeader(text = "Blockchain Anchors")

        DmvpCard {
            DmvpCertRow(
                label = "Bitcoin (OTS)",
                value = "Block #805,123 \u2713",
                valueColor = CyanPrimary,
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(thickness = 1.dp, color = DividerSubtle)
            Spacer(Modifier.height(10.dp))
            DmvpCertRow(
                label = "Sigstore Rekor",
                value = "Log #48291",
                valueColor = CyanPrimary,
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(thickness = 1.dp, color = DividerSubtle)
            Spacer(Modifier.height(10.dp))
            DmvpCertRow(
                label = "Arbitrum L2",
                value = "Synced",
                valueColor = PurplePrimary,
                isLast = true,
            )
        }

        Spacer(Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════
        // Recent Activity
        // ═══════════════════════════════════════════════════════
        DmvpSectionHeader(text = "Recent Activity")

        DmvpAssetRow(
            icon = "\uD83D\uDCC4",
            name = "Project_Report.pdf",
            meta = "uaid_5_t1_01J3Z\u2026 \u2022 L1 Match",
            badge = "Verified",
            badgeVariant = BadgeVariant.OK,
            onClick = {},
        )
        Spacer(Modifier.height(8.dp))
        DmvpAssetRow(
            icon = "\uD83C\uDFB5",
            name = "Voice_Over.mp3",
            meta = "uaid_5_t1_02K9M\u2026 \u2022 L8 Signal",
            badge = "AI Flag",
            badgeVariant = BadgeVariant.WARN,
            onClick = {},
        )
    }
}
