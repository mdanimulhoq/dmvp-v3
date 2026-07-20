/**
 * app/src/main/java/com/dmvp/app/ui/auth/SignupScreen.kt
 *
 * UDOVP V2 — Sign Up Screen (cyberpunk/terminal aesthetic)
 * Follows docs/ui-v2.html #s-signup design
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
fun SignupScreen(
    onSignupSuccess: (String) -> Unit,  // passes email for email-verify screen
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    // Navigate to email verify on successful signup
    LaunchedEffect(uiState.message) {
        if (uiState.message != null && uiState.user != null) {
            onSignupSuccess(email)
        }
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

        // Icon
        Text("\u2728", fontSize = 36.sp)
        Spacer(Modifier.height(8.dp))

        // Title
        Text(
            text = "Create Account",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            letterSpacing = (-0.5).sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Register to start protecting your assets",
            fontSize = 13.sp,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(30.dp))

        // Full Name input
        DmvpInput(
            value = name,
            onValueChange = { name = it; localError = null },
            placeholder = "Full Name",
        )
        Spacer(Modifier.height(12.dp))

        // Email input
        DmvpInput(
            value = email,
            onValueChange = { email = it; localError = null },
            placeholder = "Email Address",
        )
        Spacer(Modifier.height(12.dp))

        // Password input
        DmvpInput(
            value = password,
            onValueChange = { password = it; localError = null },
            placeholder = "Password (min 8 chars)",
            isPassword = true,
        )
        Spacer(Modifier.height(12.dp))

        // Confirm Password input
        DmvpInput(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; localError = null },
            placeholder = "Confirm Password",
            isPassword = true,
        )

        // Error display (local validation or API)
        val displayError = localError ?: uiState.error
        if (displayError != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = displayError,
                color = StatusError,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(24.dp))

        // Create Account button
        DmvpButton(
            text = if (uiState.isLoading) "Creating Account..." else "Create Account",
            onClick = {
                localError = when {
                    name.isBlank() -> "Name is required"
                    email.isBlank() -> "Email is required"
                    password.length < 8 -> "Password must be at least 8 characters"
                    password != confirmPassword -> "Passwords do not match"
                    else -> null
                }
                if (localError == null) {
                    viewModel.signUp(email, password, name)
                }
            },
            enabled = !uiState.isLoading
                    && name.isNotBlank()
                    && email.isNotBlank()
                    && password.isNotBlank()
                    && confirmPassword.isNotBlank(),
        )
        Spacer(Modifier.height(12.dp))

        // Google Sign-Up button
        DmvpButton(
            text = "\uD83D\uDD35 Sign up with Google",
            onClick = { viewModel.signInWithGoogle() },
            variant = ButtonVariant.SECONDARY,
            enabled = !uiState.isLoading,
        )

        Spacer(Modifier.weight(1f))

        // Sign In link
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 24.dp),
        ) {
            Text(
                text = "Already have an account? ",
                fontSize = 12.sp,
                color = TextMuted,
            )
            Text(
                text = "Sign In",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = CyanPrimary,
                modifier = Modifier.clickable { onNavigateToLogin() },
            )
        }
    }
}
