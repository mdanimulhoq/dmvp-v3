/**
 * app/src/main/java/com/dmvp/app/ui/screens/RegisterScreen.kt
 *
 * UDOVP V2 — Register Screen (cyberpunk/terminal aesthetic)
 * Follows docs/ui-v2.html #s-register design
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
import com.dmvp.app.ui.viewmodel.RegisterViewModel
import com.dmvp.app.ui.viewmodel.RegistrationStep
import com.dmvp.app.ui.viewmodel.isReadyToSubmit
import timber.log.Timber

@Composable
fun RegisterScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEvidenceDetail: (String) -> Unit,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: RegisterViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    var selectedModality by remember { mutableStateOf("Image") }

    LaunchedEffect(uiState.isRegistered) {
        val registrationResult = uiState.registrationResult
        if (uiState.isRegistered && registrationResult != null) {
            onNavigateToEvidenceDetail(registrationResult.evidenceId)
        }
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
            text = "Register Asset",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            letterSpacing = (-0.5).sp,
        )
        Text(
            text = "Select file type and upload to generate cryptographic proof.",
            fontSize = 13.sp,
            color = TextMuted,
        )
        Spacer(Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════
        // 1. Select Modality
        // ═══════════════════════════════════════════════════════
        DmvpSectionHeader(text = "1. Select Modality")

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ModalityTile(
                    icon = "\uD83D\uDDBC\uFE0F",
                    label = "Image",
                    selected = selectedModality == "Image",
                    onClick = { selectedModality = "Image" },
                    modifier = Modifier.weight(1f),
                )
                ModalityTile(
                    icon = "\uD83D\uDCC4",
                    label = "PDF",
                    selected = selectedModality == "PDF",
                    onClick = { selectedModality = "PDF" },
                    modifier = Modifier.weight(1f),
                )
                ModalityTile(
                    icon = "\uD83D\uDCBB",
                    label = "Code",
                    selected = selectedModality == "Code",
                    onClick = { selectedModality = "Code" },
                    modifier = Modifier.weight(1f),
                )
            }
            // Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ModalityTile(
                    icon = "\uD83C\uDFE5",
                    label = "Video",
                    selected = selectedModality == "Video",
                    onClick = { selectedModality = "Video" },
                    modifier = Modifier.weight(1f),
                )
                ModalityTile(
                    icon = "\uD83C\uDFB5",
                    label = "Audio",
                    selected = selectedModality == "Audio",
                    onClick = { selectedModality = "Audio" },
                    modifier = Modifier.weight(1f),
                )
                ModalityTile(
                    icon = "\uD83D\uDCE6",
                    label = "3D/Bin",
                    selected = selectedModality == "3D/Bin",
                    onClick = { selectedModality = "3D/Bin" },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════
        // 2. Upload File
        // ═══════════════════════════════════════════════════════
        DmvpSectionHeader(text = "2. Upload File")

        // Upload area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
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
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "⬆️",
                    fontSize = 28.sp,
                )
                Text(
                    text = "Tap to select or drop file here",
                    fontSize = 12.sp,
                    color = TextMuted,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ═══════════════════════════════════════════════════════
        // 3. C2PA Status
        // ═══════════════════════════════════════════════════════
        DmvpSectionHeader(text = "3. C2PA Status")

        DmvpCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Content Credentials",
                    fontSize = 13.sp,
                    color = TextMuted,
                )
                DmvpBadge(
                    text = "Not Detected",
                    variant = BadgeVariant.WARN,
                )
            }
        }

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
        // Submit button
        // ═══════════════════════════════════════════════════════
        DmvpButton(
            text = if (uiState.isLoading) "Registering..." else "Generate Hash & Register",
            onClick = { viewModel.submitRegistration() },
            enabled = !uiState.isLoading && uiState.isReadyToSubmit(),
        )

        Spacer(Modifier.height(12.dp))

        // Step description
        Text(
            text = getStepDescription(uiState.currentStep),
            fontSize = 11.sp,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ═══════════════════════════════════════════════════════
// Modality Tile
// ═══════════════════════════════════════════════════════

@Composable
private fun ModalityTile(
    icon: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) CyanPrimary else BorderDefault
    val bgColor = if (selected) CyanPrimary.copy(alpha = 0.06f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.025f)

    Column(
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(bgColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            letterSpacing = 0.5.sp,
        )
    }
}

// ═══════════════════════════════════════════════════════
// Step description helper
// ═══════════════════════════════════════════════════════

private fun getStepDescription(step: RegistrationStep): String {
    return when (step) {
        RegistrationStep.IDLE -> "Ready to register"
        RegistrationStep.BUILDING_CEE -> "Building evidence envelope..."
        RegistrationStep.SIGNING -> "Signing evidence..."
        RegistrationStep.SUBMITTING -> "Submitting to registry..."
        RegistrationStep.COMPLETE -> "Registration complete!"
        RegistrationStep.ERROR -> "Error occurred"
    }
}
