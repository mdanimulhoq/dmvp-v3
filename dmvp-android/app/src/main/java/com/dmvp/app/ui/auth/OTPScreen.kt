/**
 * app/src/main/java/com/dmvp/app/ui/auth/OTPScreen.kt
 *
 * UDOVP V2 — OTP Verification Screen (cyberpunk/terminal aesthetic)
 * Follows docs/ui-v2.html #s-otp design with 6 individual digit boxes
 *
 * PR 2: Auth Flow Screens
 */

package com.dmvp.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmvp.app.ui.components.*
import com.dmvp.app.ui.theme.*

@Composable
fun OTPScreen(
    email: String,
    onOTPVerified: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 6 individual OTP digits
    var otpValues by remember { mutableStateOf(List(6) { "" }) }
    val focusRequesters = remember { List(6) { FocusRequester() } }

    // Timer state (10 minutes countdown)
    var timeLeft by remember { mutableStateOf(600) } // 10 min in seconds
    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            kotlinx.coroutines.delay(1000L)
            timeLeft--
        }
    }
    val minutes = timeLeft / 60
    val seconds = timeLeft % 60
    val timerText = String.format("%02d:%02d", minutes, seconds)

    // Navigate on success
    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) onOTPVerified()
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

        // Email icon
        Text("\uD83D\uDCE7", fontSize = 36.sp)
        Spacer(Modifier.height(8.dp))

        // Title
        Text(
            text = "Verify OTP",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            letterSpacing = (-0.5).sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "6-digit code sent to your email",
            fontSize = 13.sp,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = email,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = CyanPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        // 6 OTP input boxes
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Spacer to center the boxes
            Spacer(Modifier.weight(1f))
            for (i in 0 until 6) {
                OtpDigitBox(
                    value = otpValues[i],
                    modifier = Modifier
                        .width(44.dp)
                        .height(52.dp)
                        .focusRequester(focusRequesters[i]),
                    onValueChange = { newValue ->
                        if (newValue.length <= 1 && newValue.all { it.isDigit() }) {
                            val newValues = otpValues.toMutableList()
                            newValues[i] = newValue
                            otpValues = newValues

                            // Auto-advance focus
                            if (newValue.isNotEmpty() && i < 5) {
                                focusRequesters[i + 1].requestFocus()
                            }
                        }
                    },
                    onFocus = {
                        // If current box is empty and previous has value, allow editing
                    },
                )
            }
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))

        // Timer
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Expires in ",
                fontSize = 12.sp,
                color = TextMuted,
            )
            Text(
                text = timerText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = CyanPrimary,
            )
        }

        Spacer(Modifier.height(20.dp))

        // Error message
        if (uiState.error != null) {
            Text(
                text = uiState.error ?: "",
                color = StatusError,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }

        // Success message
        if (uiState.message != null && !uiState.loginSuccess) {
            Text(
                text = uiState.message ?: "",
                color = CyanPrimary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }

        // Verify button
        val fullOtp = otpValues.joinToString("")
        DmvpButton(
            text = if (uiState.isLoading) "Verifying..." else "Verify & Continue",
            onClick = { viewModel.verifyOTP(email, fullOtp) },
            enabled = !uiState.isLoading && fullOtp.length == 6,
        )

        Spacer(Modifier.height(16.dp))

        // Resend OTP
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Didn't receive? ",
                fontSize = 12.sp,
                color = TextMuted,
            )
            Text(
                text = "Resend OTP",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = CyanPrimary,
                modifier = Modifier.clickable { viewModel.resendOTP(email) },
            )
        }

        Spacer(Modifier.weight(1f))
    }
}

// Individual OTP digit box
@Composable
private fun OtpDigitBox(
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
    onFocus: () -> Unit = {},
) {
    val isFocused = value.isNotEmpty()
    val borderColor = if (isFocused) CyanPrimary else BorderDefault

    Box(
        modifier = modifier
            .border(2.dp, borderColor, RoundedCornerShape(10.dp))
            .background(
                Color(0xFF000000).copy(alpha = 0.4f),
                RoundedCornerShape(10.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize(),
            textStyle = TextStyle(
                fontFamily = SpaceMonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            decorationBox = { innerTextField ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    innerTextField()
                }
            },
        )
    }
}


