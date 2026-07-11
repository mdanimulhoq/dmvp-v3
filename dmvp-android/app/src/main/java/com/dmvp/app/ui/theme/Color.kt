/**
 * app/src/main/java/com/dmvp/app/ui/theme/Color.kt
 *
 * Color definitions for DMVP v3.0 Android app.
 * Dark theme by default with deep purple primary and cyan accent.
 * Provides both light and dark color palettes.
 */

package com.dmvp.app.ui.theme

import androidx.compose.ui.graphics.Color

// Primary colors (deep purple)
val DeepPurple900 = Color(0xFF1A0033)
val DeepPurple800 = Color(0xFF2D004D)
val DeepPurple700 = Color(0xFF400066)
val DeepPurple600 = Color(0xFF5C0088)
val DeepPurple500 = Color(0xFF7A00B3)
val DeepPurple400 = Color(0xFF9A00D9)
val DeepPurple200 = Color(0xFFC499E0)
val DeepPurple100 = Color(0xFFE1CCF0)

// Accent colors (cyan)
val CyanBright = Color(0xFF00E5FF)
val Cyan400 = Color(0xFF00B8D4)
val Cyan100 = Color(0xFFB3F5FF)
val Cyan700 = Color(0xFF00838F)

// Neutral / surface colors for dark theme
val DarkSurface = Color(0xFF121212)
val DarkSurfaceVariant = Color(0xFF1E1E1E)
val DarkSurfaceContainer = Color(0xFF2A2A2A)
val DarkSurfaceContainerHigh = Color(0xFF333333)

// Neutral / surface colors for light theme
val LightSurface = Color(0xFFF5F5F5)
val LightSurfaceVariant = Color(0xFFE8E8E8)
val LightSurfaceContainer = Color(0xFFDDDDDD)

// Text colors
val TextPrimaryDark = Color(0xFFE0E0E0)
val TextSecondaryDark = Color(0xFFB0B0B0)
val TextTertiaryDark = Color(0xFF808080)

val TextPrimaryLight = Color(0xFF1A1A1A)
val TextSecondaryLight = Color(0xFF4D4D4D)
val TextTertiaryLight = Color(0xFF808080)

// Status / feedback colors
val Success = Color(0xFF4CAF50)
val Warning = Color(0xFFFFC107)
val Error = Color(0xFFE53935)
val Info = Color(0xFF2196F3)

// Divider / border
val DividerDark = Color(0xFF3D3D3D)
val DividerLight = Color(0xFFCCCCCC)

// Trust tier badges
val TierABadge = Color(0xFF00E676)
val TierBBadge = Color(0xFFFFD740)
val TierCBadge = Color(0xFFFF6D00)
val TierDBadge = Color(0xFFE53935)

// Evidence quality colors
val HighQuality = Success
val ModerateQuality = Warning
val LowQuality = Error

// Verdict colors
val IntegrityMatch = Success
val IntegrityNoMatch = Error
val SimilarityStrong = Color(0xFF00BCD4)
val SimilarityWeak = Color(0xFFFFA726)
val SimilarityNone = Error

// Background for dark theme
val DarkBackground = Color(0xFF0A0A0A)
