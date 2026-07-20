/**
 * app/src/main/java/com/dmvp/app/ui/auth/EmailVerifyScreen.kt
 *
 * UDOVP V2 — Email Verification Screen (cyberpunk/terminal aesthetic)
 * Follows docs/ui-v2.html #s-email-verify design
 *
 * PR 2: Auth Flow Screens
 */

package com.dmvp.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmvp.app.ui.components.*
import com.dmvp.app.ui.theme.*

@Composable
fun EmailVerifyScreen(
    email: String,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBase)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.weight(1f))

        // Mail icon
        Text("\uD83D\uDCEC", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))

        // Title
        Text(
            text = "Check Your Email",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            letterSpacing = (-0.5).sp,
        )
        Spacer(Modifier.height(4.dp))

        // Subtitle with email
        Text(
            text = "We sent a verification link to",
            fontSize = 13.sp,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
        Text(
            text = email,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = CyanPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(30.dp))

        // Info card
        DmvpCard {
            Text(
                text = "Click the link in the email to verify your account. The link expires in 24 hours.",
                fontSize = 13.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            // Success chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                DmvpBadge(
                    text = "\u2713 Email sent successfully",
                    variant = BadgeVariant.OK,
                )
            }
        }

        // Error message
        if (uiState.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = uiState.error ?: "",
                color = StatusError,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Message
        if (uiState.message != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = uiState.message ?: "",
                color = CyanPrimary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))

        // "I've Verified — Sign In" button
        DmvpButton(
            text = "I've Verified \u2014 Sign In",
            onClick = onNavigateToLogin,
        )

        Spacer(Modifier.height(14.dp))

        // Resend verification email
        Text(
            text = "Resend Verification Email",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = CyanPrimary,
            modifier = Modifier.clickable {
                viewModel.resendVerificationEmail(email)
            },
        )

        Spacer(Modifier.weight(1f))
    }
}
