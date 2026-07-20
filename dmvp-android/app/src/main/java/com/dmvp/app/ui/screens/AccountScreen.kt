/**
 * app/src/main/java/com/dmvp/app/ui/screens/AccountScreen.kt
 *
 * UDOVP V2 — Account Screen (cyberpunk/terminal aesthetic)
 * Follows docs/ui-v2.html #s-account design
 *
 * PR 5: Assets + Claims + Account + Devices
 */

package com.dmvp.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmvp.app.ui.components.*
import com.dmvp.app.ui.theme.*

@Composable
fun AccountScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgBase)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 8.dp, bottom = 100.dp),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = "← Back",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = CyanPrimary,
                modifier = Modifier.clickable { onNavigateBack() },
            )
        }

        // Profile section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        brush = Brush.linearGradient(GradientAccent),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "AH",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = androidx.compose.ui.graphics.Color.Black,
                )
            }
            Spacer(Modifier.height(12.dp))

            // Name
            Text(
                text = "Animul Hoq",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(Modifier.height(4.dp))

            // Tier
            Text(
                text = "Premium Member • FREE Tier",
                fontSize = 12.sp,
                color = CyanPrimary,
            )
        }

        // ═══════════════════════════════════════════════════════
        // Profile Details
        // ═══════════════════════════════════════════════════════
        DmvpCard {
            DmvpSectionHeader(text = "Profile Details")

            DmvpCertRow(
                label = "User ID",
                value = "usr_8839-udv",
            )
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.HorizontalDivider(
                thickness = 1.dp,
                color = DividerSubtle,
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Email",
                    fontSize = 13.sp,
                    color = TextMuted,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "ah***@gmail.com",
                        fontSize = 11.sp,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    DmvpSwitch(
                        checked = true,
                        onCheckedChange = { /* TODO: Toggle email visibility */ },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.HorizontalDivider(
                thickness = 1.dp,
                color = DividerSubtle,
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Phone",
                    fontSize = 13.sp,
                    color = TextMuted,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "+8801***",
                        fontSize = 11.sp,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    DmvpSwitch(
                        checked = false,
                        onCheckedChange = { /* TODO: Toggle phone visibility */ },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ═══════════════════════════════════════════════════════
        // Security
        // ═══════════════════════════════════════════════════════
        DmvpCard {
            DmvpSectionHeader(text = "Security")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Biometric Lock",
                    fontSize = 13.sp,
                    color = TextMuted,
                )
                DmvpSwitch(
                    checked = true,
                    onCheckedChange = { /* TODO: Toggle biometric */ },
                )
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.HorizontalDivider(
                thickness = 1.dp,
                color = DividerSubtle,
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Post-Quantum Sign",
                    fontSize = 13.sp,
                    color = TextMuted,
                )
                DmvpSwitch(
                    checked = true,
                    onCheckedChange = { /* TODO: Toggle PQ sign */ },
                )
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.HorizontalDivider(
                thickness = 1.dp,
                color = DividerSubtle,
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Verification Depth",
                    fontSize = 13.sp,
                    color = TextMuted,
                )
                Text(
                    text = "Standard ›",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = CyanPrimary,
                    modifier = Modifier.clickable { /* TODO: Navigate to depth settings */ },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ═══════════════════════════════════════════════════════
        // Devices
        // ═══════════════════════════════════════════════════════
        DmvpCard {
            DmvpSectionHeader(text = "Devices")

            DmvpAssetRow(
                icon = "📱",
                name = "Samsung Galaxy S25",
                meta = "Tier A • Hardware-backed",
                badge = "Active",
                badgeVariant = BadgeVariant.OK,
                onClick = onNavigateToDevices,
            )
            Spacer(Modifier.height(8.dp))
            DmvpAssetRow(
                icon = "💻",
                name = "MacBook Pro",
                meta = "Tier B • Software",
                badge = "Linked",
                badgeVariant = BadgeVariant.INFO,
                onClick = onNavigateToDevices,
            )
        }

        Spacer(Modifier.height(16.dp))

        // ═══════════════════════════════════════════════════════
        // Privacy & Data
        // ═══════════════════════════════════════════════════════
        DmvpCard {
            DmvpSectionHeader(text = "Privacy & Data")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Auto-strip EXIF/GPS",
                    fontSize = 13.sp,
                    color = TextMuted,
                )
                DmvpSwitch(
                    checked = true,
                    onCheckedChange = { /* TODO: Toggle EXIF strip */ },
                )
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.HorizontalDivider(
                thickness = 1.dp,
                color = DividerSubtle,
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Inject C2PA Credentials",
                    fontSize = 13.sp,
                    color = TextMuted,
                )
                DmvpSwitch(
                    checked = true,
                    onCheckedChange = { /* TODO: Toggle C2PA inject */ },
                )
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.HorizontalDivider(
                thickness = 1.dp,
                color = DividerSubtle,
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Clear Cache",
                    fontSize = 13.sp,
                    color = TextMuted,
                )
                Text(
                    text = "Clear (24 MB) ›",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = StatusError,
                    modifier = Modifier.clickable { /* TODO: Clear cache */ },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ═══════════════════════════════════════════════════════
        // Activity Log
        // ═══════════════════════════════════════════════════════
        DmvpCard {
            DmvpSectionHeader(text = "Activity Log")

            // Timeline
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TimelineItem(
                    time = "Today 10:42 AM",
                    text = "Registered Project_Report.pdf",
                )
                TimelineItem(
                    time = "Today 09:15 AM",
                    text = "Verified sunset_photo.jpg — L1 Match",
                )
                TimelineItem(
                    time = "Yesterday 06:30 PM",
                    text = "Ownership claim filed for asset #01J3Z",
                )
                TimelineItem(
                    time = "Jul 18, 2026",
                    text = "Account created",
                    isLast = true,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ═══════════════════════════════════════════════════════
        // Terms & Conditions
        // ═══════════════════════════════════════════════════════
        DmvpCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { /* TODO: Navigate to T&C */ },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Terms & Conditions",
                    fontSize = 13.sp,
                    color = TextMuted,
                )
                Text(
                    text = "›",
                    fontSize = 16.sp,
                    color = TextMuted,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ═══════════════════════════════════════════════════════
        // Logout button
        // ═══════════════════════════════════════════════════════
        DmvpButton(
            text = "Logout",
            onClick = onLogout,
            variant = ButtonVariant.DANGER,
        )
    }
}

// ═══════════════════════════════════════════════════════
// Timeline Item
// ═══════════════════════════════════════════════════════

@Composable
private fun TimelineItem(
    time: String,
    text: String,
    isLast: Boolean = false,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Timeline line (if not last)
        if (!isLast) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(BorderDefault)
                    .align(Alignment.CenterStart)
                    .offset(x = (-14).dp),
            )
        }

        // Timeline dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(CyanPrimary, CircleShape)
                .align(Alignment.CenterStart)
                .offset(x = (-17).dp),
        )

        // Content
        Column(
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Text(
                text = time,
                fontSize = 10.sp,
                color = TextMuted,
            )
            Text(
                text = text,
                fontSize = 12.sp,
                color = TextPrimary,
            )
        }
    }
}
