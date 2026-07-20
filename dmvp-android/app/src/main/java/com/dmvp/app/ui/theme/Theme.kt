/**
 * app/src/main/java/com/dmvp/app/ui/theme/Theme.kt
 *
 * UDOVP V2 Theme — Cyberpunk/terminal aesthetic
 * Dark theme only (no light theme) for consistent forensic feel.
 * Primary: Cyan (#00FFB4), Secondary: Purple (#A78BFA)
 *
 * Reference: docs/ui-v2.html
 */

package com.dmvp.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════════════════
// Dark Color Scheme (UDOVP V2)
// ═══════════════════════════════════════════════════════

private val DmvpDarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color.Black,
    primaryContainer = CyanDim,
    onPrimaryContainer = Color.White,

    secondary = PurplePrimary,
    onSecondary = Color.Black,
    secondaryContainer = PurpleDeep,
    onSecondaryContainer = Color.White,

    tertiary = PinkAccent,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF831843),
    onTertiaryContainer = Color.White,

    background = BgBase,
    onBackground = TextPrimary,

    surface = SurfaceGlass,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = SurfaceCard,
    surfaceContainerHigh = SurfaceElevated,

    error = StatusError,
    onError = Color.White,
    errorContainer = Color(0xFF8B0000),
    onErrorContainer = Color.White,

    outline = BorderDefault,
    outlineVariant = DividerSubtle,
    scrim = Color.Black.copy(alpha = 0.6f),

    inverseSurface = Color(0xFFE2E8F0),
    inverseOnSurface = Color(0xFF08090D),
    inversePrimary = CyanDim,
)

// ═══════════════════════════════════════════════════════
// Theme Composable
// ═══════════════════════════════════════════════════════

@Composable
fun DmvpTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DmvpDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as androidx.activity.ComponentActivity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
            // Status bar matches background
            window.statusBarColor = BgBase.toArgb()
            window.navigationBarColor = BgBase.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DmvpTypography,
        shapes = DmvpShapes,
        content = content
    )
}

// ═══════════════════════════════════════════════════════
// Theme Accessors (convenience)
// ═══════════════════════════════════════════════════════

object DmvpTokens {
    val colors: DmvpColors get() = DmvpColors
    val shapes get() = DmvpShapes
    val typography get() = DmvpTypography
}

object DmvpColors {
    val cyan = CyanPrimary
    val purple = PurplePrimary
    val pink = PinkAccent
    val amber = AmberAccent
    val red = RedAccent

    val bg = BgBase
    val surface = SurfaceGlass
    val card = SurfaceCard
    val elevated = SurfaceElevated

    val border = BorderDefault
    val borderHi = BorderHighlight
    val divider = DividerSubtle

    val textPrimary = TextPrimary
    val textSecondary = TextSecondary
    val textMuted = TextMuted
    val textDim = TextDim

    val success = StatusSuccess
    val warning = StatusWarning
    val error = StatusError
    val info = StatusInfo
}
