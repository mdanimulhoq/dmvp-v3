/**
 * app/src/main/java/com/dmvp/app/ui/components/GoogleSignInButton.kt
 *
 * UDOVP V2 — Google Sign-In Button (dark theme, matches cyberpunk aesthetic)
 * Updated for PR 2 to match V2 design system.
 */

package com.dmvp.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmvp.app.ui.theme.*

@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String = "Continue with Google",
) {
    val bgColor = if (enabled) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.02f)
    val textColor = if (enabled) TextPrimary else TextMuted

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .border(1.dp, BorderDefault, RoundedCornerShape(14.dp))
            .background(bgColor, RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // Google "G" icon placeholder (emoji — replace with drawable when available)
        Text(
            text = "\uD83D\uDD35",
            fontSize = 18.sp,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            letterSpacing = 0.5.sp,
        )
    }
}
