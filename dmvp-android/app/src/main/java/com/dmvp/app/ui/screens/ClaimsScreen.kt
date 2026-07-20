/**
 * app/src/main/java/com/dmvp/app/ui/screens/ClaimsScreen.kt
 *
 * UDOVP V2 — Claims Screen (cyberpunk/terminal aesthetic)
 * Follows docs/ui-v2.html #s-claims design
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
fun ClaimsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToClaimDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNewClaimForm by remember { mutableStateOf(false) }
    var claimUAID by remember { mutableStateOf("") }
    var claimType by remember { mutableStateOf("Ownership") }
    var claimEvidence by remember { mutableStateOf("") }

    // Mock data - replace with actual ViewModel
    val claims = remember {
        listOf(
            Triple("📄", "Project_Report.pdf", "Ownership • Jul 20"),
            Triple("🖼️", "sunset_photo.jpg", "License • Jul 19"),
            Triple("🎵", "Voice_Over.mp3", "Copyright • Jul 18"),
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
            text = "Ownership Claims",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            letterSpacing = (-0.5).sp,
        )
        Text(
            text = "Manage ownership claims and licenses.",
            fontSize = 13.sp,
            color = TextMuted,
        )
        Spacer(Modifier.height(18.dp))

        // New Claim button
        DmvpButton(
            text = "+ New Claim",
            onClick = { showNewClaimForm = !showNewClaimForm },
        )

        Spacer(Modifier.height(18.dp))

        // New Claim Form (if expanded)
        if (showNewClaimForm) {
            DmvpCard {
                Text(
                    text = "New Claim",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanPrimary,
                )
                Spacer(Modifier.height(12.dp))

                // UAID input
                DmvpInput(
                    value = claimUAID,
                    onValueChange = { claimUAID = it },
                    placeholder = "Enter UAID",
                )
                Spacer(Modifier.height(12.dp))

                // Claim type
                DmvpInput(
                    value = claimType,
                    onValueChange = { claimType = it },
                    placeholder = "Claim Type (Ownership, License, Copyright)",
                )
                Spacer(Modifier.height(12.dp))

                // Evidence
                DmvpInput(
                    value = claimEvidence,
                    onValueChange = { claimEvidence = it },
                    placeholder = "Supporting Evidence (optional)",
                )
                Spacer(Modifier.height(12.dp))

                DmvpButton(
                    text = "Submit Claim",
                    onClick = { /* TODO: Submit claim */ },
                    enabled = claimUAID.isNotBlank() && claimType.isNotBlank(),
                )
            }

            Spacer(Modifier.height(18.dp))
        }

        // Claims list
        DmvpSectionHeader(text = "My Claims")

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            claims.forEach { (icon, name, meta) ->
                DmvpAssetRow(
                    icon = icon,
                    name = name,
                    meta = meta,
                    badge = "Active",
                    badgeVariant = BadgeVariant.OK,
                    onClick = { onNavigateToClaimDetail(name) },
                )
            }
        }
    }
}
