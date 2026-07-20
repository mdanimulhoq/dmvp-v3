/**
 * app/src/main/java/com/dmvp/app/ui/screens/AssetsScreen.kt
 *
 * UDOVP V2 — Assets Screen (cyberpunk/terminal aesthetic)
 * Follows docs/ui-v2.html #s-assets design
 *
 * PR 5: Assets + Claims + Account + Devices
 */

package com.dmvp.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmvp.app.ui.components.*
import com.dmvp.app.ui.theme.*

@Composable
fun AssetsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAssetDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }

    // Mock data - replace with actual ViewModel
    val assets = remember {
        listOf(
            Triple("📄", "Project_Report.pdf", "uaid_5_t1_01J3Z… • Jul 20"),
            Triple("🖼️", "sunset_photo.jpg", "uaid_5_t1_02K9M… • Jul 19"),
            Triple("🎵", "Voice_Over.mp3", "uaid_5_t1_03L7N… • Jul 18"),
            Triple("💻", "main.py", "uaid_5_t1_04M2P… • Jul 18"),
        )
    }

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

        // Title
        Text(
            text = "My Assets",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            letterSpacing = (-0.5).sp,
        )
        Text(
            text = "Ownership certificates and registered files.",
            fontSize = 13.sp,
            color = TextMuted,
        )
        Spacer(Modifier.height(18.dp))

        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .border(1.dp, BorderDefault, RoundedCornerShape(14.dp))
                .background(
                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f),
                    RoundedCornerShape(14.dp),
                )
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DmvpInput(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search by UAID or name...",
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(60.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(GradientButton),
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { /* TODO: Search */ },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "GO",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.Black,
                    letterSpacing = 1.sp,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // Assets list
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            assets.forEach { (icon, name, meta) ->
                DmvpAssetRow(
                    icon = icon,
                    name = name,
                    meta = meta,
                    badge = "UEE v5",
                    badgeVariant = BadgeVariant.OK,
                    onClick = { onNavigateToAssetDetail(name) },
                )
            }
        }
    }
}
