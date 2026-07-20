/**
 * app/src/main/java/com/dmvp/app/ui/screens/CompareScreen.kt
 *
 * UDOVP V2 — Compare Screen (cyberpunk/terminal aesthetic)
 * Follows docs/ui-v2.html #s-compare design
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
import androidx.compose.material3.HorizontalDivider
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
import com.dmvp.app.ui.viewmodel.CompareViewModel
import com.dmvp.app.ui.viewmodel.CompareStep
import com.dmvp.app.ui.viewmodel.CompareOutcome
import timber.log.Timber

@Composable
fun CompareScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEvidenceDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CompareViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

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
            text = "Compare / Similarity",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            letterSpacing = (-0.5).sp,
        )
        Text(
            text = "Check if a file is original or an edited derivative.",
            fontSize = 13.sp,
            color = TextMuted,
        )
        Spacer(Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════
        // 1. Original (Registered)
        // ═══════════════════════════════════════════════════════
        DmvpSectionHeader(text = "1. Original (Registered)")

        // UAID input
        DmvpInput(
            value = "",
            onValueChange = { /* TODO: Bind to ViewModel */ },
            placeholder = "Enter Registered UAID",
        )
        Spacer(Modifier.height(12.dp))

        // Upload original
        UploadBox(
            text = "Or upload original file",
            onClick = { /* TODO: File picker */ },
        )

        Spacer(Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════
        // 2. File to Check
        // ═══════════════════════════════════════════════════════
        DmvpSectionHeader(text = "2. File to Check")

        // Upload suspicious file
        UploadBox(
            icon = "\uD83D\uDCC1",
            text = "Upload suspicious/edited file",
            onClick = { /* TODO: File picker */ },
        )

        Spacer(Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════
        // Run Comparison button
        // ═══════════════════════════════════════════════════════
        DmvpButton(
            text = if (uiState.isLoading) "Comparing..." else "Run Comparison",
            onClick = { /* TODO: Trigger comparison */ },
            enabled = !uiState.isLoading,
        )

        Spacer(Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════
        // Error display
        // ═══════════════════════════════════════════════════════
        val error = uiState.error
        if (error != null) {
            Text(
                text = error,
                color = StatusError,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }

        // ═══════════════════════════════════════════════════════
        // Comparison Report (placeholder - will show when comparison completes)
        // ═══════════════════════════════════════════════════════
        if (uiState.step == CompareStep.RESULT && uiState.outcome != null) {
            ComparisonReport(
                outcome = uiState.outcome!!,
                similarityScore = uiState.similarityScore,
                onOpenEvidenceDetail = { evidenceId ->
                    onNavigateToEvidenceDetail(evidenceId)
                },
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// Upload Box
// ═══════════════════════════════════════════════════════

@Composable
private fun UploadBox(
    text: String,
    onClick: () -> Unit,
    icon: String? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .border(
                2.dp,
                CyanPrimary.copy(alpha = 0.2f),
                RoundedCornerShape(14.dp),
            )
            .background(
                CyanPrimary.copy(alpha = 0.02f),
                RoundedCornerShape(14.dp),
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Text(icon, fontSize = 20.sp)
            }
            Text(
                text = text,
                fontSize = 12.sp,
                color = TextMuted,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// Comparison Report
// ═══════════════════════════════════════════════════════

@Composable
private fun ComparisonReport(
    outcome: CompareOutcome,
    similarityScore: Double?,
    onOpenEvidenceDetail: (String) -> Unit,
) {
    val (title, color, description) = when (outcome) {
        CompareOutcome.EXACT_MATCH -> Triple(
            "Exact Match",
            StatusSuccess,
            "Both files are byte-for-byte identical.",
        )
        CompareOutcome.CANONICAL_MATCH -> Triple(
            "Canonical Match",
            StatusSuccess,
            "Content matches after re-encode/metadata strip.",
        )
        CompareOutcome.SIMILAR_MATCH -> Triple(
            "Similar Match",
            StatusWarning,
            "Perceptual fingerprint matched. Likely a derivative.",
        )
        CompareOutcome.NO_MATCH -> Triple(
            "No Match",
            StatusError,
            "Files do not match. Different media.",
        )
        CompareOutcome.REFERENCE_NOT_REGISTERED -> Triple(
            "Reference Not Registered",
            StatusWarning,
            "Register the reference first to see provenance.",
        )
    }

    DmvpCard(glow = true) {
        // Title
        Text(
            text = "Comparison Report",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = CyanPrimary,
        )
        Spacer(Modifier.height(14.dp))

        // Progress bars
        if (similarityScore != null) {
            // Visual Match
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Visual Match (L3/L4)",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                    Text(
                        text = "${(similarityScore * 100).toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary,
                    )
                }
                Spacer(Modifier.height(4.dp))
                DmvpProgressBar(progress = similarityScore.toFloat())
            }

            Spacer(Modifier.height(14.dp))

            // Structural
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Structural (L2)",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                    Text(
                        text = "78.1%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary,
                    )
                }
                Spacer(Modifier.height(4.dp))
                DmvpProgressBar(progress = 0.781f)
            }

            Spacer(Modifier.height(14.dp))
        }

        // Verdict
        DmvpCertRow(
            label = "Verdict",
            value = title,
            valueColor = color,
        )
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(thickness = 1.dp, color = DividerSubtle)
        Spacer(Modifier.height(10.dp))

        // AI Signals
        DmvpCertRow(
            label = "AI Signals",
            value = "Brightness +10%, Cropped",
            valueColor = CyanPrimary,
        )
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(thickness = 1.dp, color = DividerSubtle)
        Spacer(Modifier.height(10.dp))

        // C2PA
        DmvpCertRow(
            label = "C2PA",
            value = "Absent",
        )
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(thickness = 1.dp, color = DividerSubtle)
        Spacer(Modifier.height(10.dp))

        // Source link
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Source",
                fontSize = 13.sp,
                color = TextMuted,
            )
            Text(
                text = "View Original ›",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = PurplePrimary,
                modifier = Modifier.clickable { /* TODO: Navigate to original */ },
            )
        }
    }
}
