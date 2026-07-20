/**
 * app/src/main/java/com/dmvp/app/ui/screens/VerifyScreen.kt
 *
 * UDOVP V2 — Verify Screen (cyberpunk/terminal aesthetic)
 * Follows docs/ui-v2.html #s-verify design with 10-layer verification display
 *
 * PR 4: Register + Verify + Compare
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmvp.app.ui.components.*
import com.dmvp.app.ui.theme.*
import com.dmvp.app.ui.viewmodel.VerifyViewModel
import timber.log.Timber

@Composable
fun VerifyScreen(
    onNavigateBack: () -> Unit,
    onNavigateToVerdictDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: VerifyViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    var uaidQuery by remember { mutableStateOf("") }

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
            text = "Verification Engine",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            letterSpacing = (-0.5).sp,
        )
        Text(
            text = "10-layer verification from exact hash to ZK proofs.",
            fontSize = 13.sp,
            color = TextMuted,
        )
        Spacer(Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════
        // Search by UAID
        // ═══════════════════════════════════════════════════════
        DmvpSectionHeader(text = "Search by UAID")

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
                value = uaidQuery,
                onValueChange = { uaidQuery = it },
                placeholder = "Enter UAID (e.g., uaid_5_t1...)",
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(GradientButton),
                        RoundedCornerShape(10.dp),
                    )
                    .clickable {
                        viewModel.setEvidenceIdQuery(uaidQuery)
                        viewModel.searchEvidenceById()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "CHECK",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.Black,
                    letterSpacing = 1.sp,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════
        // Or Upload File
        // ═══════════════════════════════════════════════════════
        DmvpSectionHeader(text = "Or Upload File")

        // Upload area with file info
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .border(
                    2.dp,
                    CyanPrimary.copy(alpha = 0.2f),
                    RoundedCornerShape(14.dp),
                )
                .background(
                    CyanPrimary.copy(alpha = 0.02f),
                    RoundedCornerShape(14.dp),
                )
                .clickable { /* TODO: File picker */ },
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // File icon
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            CyanPrimary.copy(alpha = 0.06f),
                            RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("\uD83D\uDDBC\uFE0F", fontSize = 18.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "sunset_photo.jpg",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    Text(
                        text = "Uploaded just now",
                        fontSize = 11.sp,
                        color = TextMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════
        // Verification Layers (10-layer display)
        // ═══════════════════════════════════════════════════════
        DmvpSectionHeader(text = "Verification Layers")

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // L1: Exact Integrity
            VerificationLayer(
                name = "L1: Exact Integrity",
                subtitle = "SHA-256 + BLAKE3 bit match",
                status = "MATCH",
                statusVariant = BadgeVariant.OK,
                borderColor = CyanPrimary,
            )
            // L2: Structural
            VerificationLayer(
                name = "L2: Structural",
                subtitle = "EXIF + container digest",
                status = "MATCH",
                statusVariant = BadgeVariant.OK,
                borderColor = CyanPrimary,
            )
            // L3: Perceptual
            VerificationLayer(
                name = "L3: Perceptual",
                subtitle = "PDQ + pHash + dHash",
                status = "MATCH",
                statusVariant = BadgeVariant.OK,
                borderColor = CyanPrimary,
            )
            // L4: AI Embedding
            VerificationLayer(
                name = "L4: AI Embedding",
                subtitle = "DINOv3 + SigLIP 2 — cosine 0.98",
                status = "SCAN",
                statusVariant = BadgeVariant.INFO,
                borderColor = PurplePrimary,
            )
            // L5: Cross-modal
            VerificationLayer(
                name = "L5: Cross-modal",
                subtitle = "SigLIP 2 semantic alignment",
                status = "SCAN",
                statusVariant = BadgeVariant.INFO,
                borderColor = TextDim,
            )
            // L8: AI Derivative
            VerificationLayer(
                name = "L8: AI Derivative",
                subtitle = "C2PA + TrustMark + SynthID",
                status = "PENDING",
                statusVariant = BadgeVariant.WARN,
                borderColor = TextDim,
            )
            // L9: Local Descriptor
            VerificationLayer(
                name = "L9: Local Descriptor",
                subtitle = "SuperPoint + LightGlue",
                status = "SCAN",
                statusVariant = BadgeVariant.INFO,
                borderColor = TextDim,
            )
            // L10: ZK Proof
            VerificationLayer(
                name = "L10: ZK Proof",
                subtitle = "Halo2 / SP1 possession proof",
                status = "R&D",
                statusVariant = BadgeVariant.INFO,
                borderColor = TextDim,
            )
        }

        Spacer(Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════
        // Trust Score Summary
        // ═══════════════════════════════════════════════════════
        DmvpCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DmvpTrustCircle(score = 96)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "High Confidence",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary,
                    )
                    Text(
                        text = "6/8 layers matched",
                        fontSize = 11.sp,
                        color = TextMuted,
                    )
                }
            }
        }

        // Error display
        val errorMsg = uiState.error
        if (errorMsg != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = errorMsg,
                color = StatusError,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// Verification Layer Row
// ═══════════════════════════════════════════════════════

@Composable
private fun VerificationLayer(
    name: String,
    subtitle: String,
    status: String,
    statusVariant: BadgeVariant,
    borderColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 3.dp,
                color = borderColor,
            )
            .background(
                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f),
                RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextMuted,
            )
        }
        DmvpBadge(
            text = status,
            variant = statusVariant,
        )
    }
}
