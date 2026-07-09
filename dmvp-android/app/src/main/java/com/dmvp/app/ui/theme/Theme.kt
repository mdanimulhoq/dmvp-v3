/**
 * app/src/main/java/com/dmvp/app/ui/theme/Theme.kt
 *
 * DMVP v3.0 application theme.
 * Uses Material3 with dark theme by default (professional forensic feel).
 * Deep purple primary, cyan accent.
 */

package com.dmvp.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Private color schemes for Material3
private val DarkColorScheme = darkColorScheme(
    primary = DeepPurple700,
    onPrimary = Color.White,
    primaryContainer = DeepPurple800,
    onPrimaryContainer = CyanBright,
    secondary = CyanBright,
    onSecondary = DeepPurple900,
    secondaryContainer = Cyan700,
    onSecondaryContainer = Color.White,
    tertiary = DeepPurple400,
    onTertiary = Color.White,
    tertiaryContainer = DeepPurple600,
    onTertiaryContainer = Color.White,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    error = Error,
    onError = Color.White,
    errorContainer = Color(0xFF8B0000),
    onErrorContainer = Color.White,
    outline = DividerDark,
    outlineVariant = DividerDark,
    scrim = Color.Black.copy(alpha = 0.6f),
    inverseSurface = LightSurface,
    inverseOnSurface = TextPrimaryLight,
    inversePrimary = DeepPurple500,
)

private val LightColorScheme = lightColorScheme(
    primary = DeepPurple600,
    onPrimary = Color.White,
    primaryContainer = DeepPurple100,
    onPrimaryContainer = DeepPurple900,
    secondary = Cyan700,
    onSecondary = Color.White,
    secondaryContainer = Cyan100,
    onSecondaryContainer = DeepPurple900,
    tertiary = DeepPurple400,
    onTertiary = Color.White,
    tertiaryContainer = DeepPurple200,
    onTertiaryContainer = DeepPurple900,
    background = LightSurface,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = Color(0xFFEEEEEE),
    error = Error,
    onError = Color.White,
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFFB71C1C),
    outline = DividerLight,
    outlineVariant = DividerLight,
    scrim = Color.Black.copy(alpha = 0.4f),
    inverseSurface = DarkSurface,
    inverseOnSurface = TextPrimaryDark,
    inversePrimary = DeepPurple400,
)

@Composable
fun DMVPTheme(
    darkTheme: Boolean = true, // Default to dark theme
    content: @Composable () -> Unit
) {
    // Force dark theme by default, but allow override
    val actualDark = darkTheme // always true unless user passes false
    val colorScheme = if (actualDark) DarkColorScheme else LightColorScheme

    // Set the status bar and navigation bar colors to match the background
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as androidx.activity.ComponentActivity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !actualDark
                isAppearanceLightNavigationBars = !actualDark
            }
            // Set navigation bar color
            window.navigationBarColor = if (actualDark) {
                androidx.core.graphics.ColorUtils.blendARGB(
                    DarkBackground.toArgb(),
                    Color.Black.toArgb(),
                    0.9f
                )
            } else {
                LightSurface.toArgb()
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DMVPTypography,
        shapes = Shapes,
        content = content
    )
}
