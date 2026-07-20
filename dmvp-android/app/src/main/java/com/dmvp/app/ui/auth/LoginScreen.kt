/**
 * app/src/main/java/com/dmvp/app/ui/auth/LoginScreen.kt
 *
 * UDOVP V2 — Login Screen (cyberpunk/terminal aesthetic)
 * Follows docs/ui-v2.html #s-login design
 *
 * PR 2: Auth Flow Screens
 */

package com.dmvp.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignup: () -> Unit,
    onNavigateToOTP: (String) -> Unit,
    viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Navigate on success
    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) onLoginSuccess()
    }
    LaunchedEffect(uiState.requiresOTP) {
        if (uiState.requiresOTP) onNavigateToOTP(email)
    }

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

        // Lock icon
        Text("\uD83D\uDD10", fontSize = 36.sp)
        Spacer(Modifier.height(8.dp))

        // Title
        Text(
            text = "Welcome Back",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            letterSpacing = (-0.5).sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Sign in to manage your digital assets",
            fontSize = 13.sp,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))

        // Email input
        DmvpInput(
            value = email,
            onValueChange = { email = it },
            placeholder = "Email Address",
        )
        Spacer(Modifier.height(12.dp))

        // Password input
        DmvpInput(
            value = password,
            onValueChange = { password = it },
            placeholder = "Password",
            isPassword = true,
        )
        Spacer(Modifier.height(24.dp))

        // Sign In button (primary gradient)
        DmvpButton(
            text = if (uiState.isLoading) "Signing In..." else "Sign In",
            onClick = { viewModel.signIn(email, password) },
            enabled = !uiState.isLoading && email.isNotBlank() && password.isNotBlank(),
        )
        Spacer(Modifier.height(12.dp))

        // Google Sign-In button (secondary)
        DmvpButton(
            text = "\uD83D\uDD35 Continue with Google",
            onClick = { viewModel.signInWithGoogle() },
            variant = ButtonVariant.SECONDARY,
            enabled = !uiState.isLoading,
        )

        // Error message
        if (uiState.error != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = uiState.error ?: "",
                color = StatusError,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.weight(1f))

        // Sign Up link
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 24.dp),
        ) {
            Text(
                text = "Don't have an account? ",
                fontSize = 12.sp,
                color = TextMuted,
            )
            Text(
                text = "Sign Up",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = CyanPrimary,
                modifier = Modifier.clickable { onNavigateToSignup() },
            )
        }
    }
}
