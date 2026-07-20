/**
 * app/src/main/java/com/dmvp/app/ui/theme/Type.kt
 *
 * UDOVP V2 Typography
 * Display: Outfit (modern, geometric sans-serif)
 * Mono: Space Mono (terminal/code aesthetic)
 *
 * Reference: docs/ui-v2.html
 */

package com.dmvp.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════
// Font Families
// ═══════════════════════════════════════════════════════

// Outfit — primary display font (download from Google Fonts or use system fallback)
val OutfitFont = FontFamily.Default // Replace with Font(R.font.outfit_regular) when fonts added

// Space Mono — monospace for code, labels, hashes
val SpaceMonoFont = FontFamily.Monospace

// ═══════════════════════════════════════════════════════
// Typography Scale
// ═══════════════════════════════════════════════════════

val DmvpTypography = Typography(
    // Display — Splash screen title
    displayLarge = TextStyle(
        fontFamily = OutfitFont,
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = OutfitFont,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = OutfitFont,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),

    // Headline — Screen titles
    headlineLarge = TextStyle(
        fontFamily = OutfitFont,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = OutfitFont,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = OutfitFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),

    // Title — Card titles, section headers
    titleLarge = TextStyle(
        fontFamily = OutfitFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = OutfitFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = OutfitFont,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),

    // Body — Main text content
    bodyLarge = TextStyle(
        fontFamily = OutfitFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = OutfitFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = OutfitFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),

    // Label — Buttons, nav items, badges
    labelLarge = TextStyle(
        fontFamily = OutfitFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = SpaceMonoFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = SpaceMonoFont,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp,
    ),
)

// ═══════════════════════════════════════════════════════
// Shapes
// ═══════════════════════════════════════════════════════

val DmvpShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

// ═══════════════════════════════════════════════════════
// Custom Text Styles
// ═══════════════════════════════════════════════════════

// Section header — "QUICK ACTIONS", "BLOCKCHAIN ANCHORS"
val SectionHeaderStyle = TextStyle(
    fontFamily = SpaceMonoFont,
    fontWeight = FontWeight.Bold,
    fontSize = 10.sp,
    letterSpacing = 2.5.sp,
    color = CyanPrimary,
)

// Hash / UAID display
val HashDisplayStyle = TextStyle(
    fontFamily = SpaceMonoFont,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    letterSpacing = 0.5.sp,
    color = TextPrimary,
)

// Stat value — large numbers
val StatValueStyle = TextStyle(
    fontFamily = OutfitFont,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 24.sp,
    lineHeight = 28.sp,
    color = TextPrimary,
)

// Stat label
val StatLabelStyle = TextStyle(
    fontFamily = SpaceMonoFont,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    letterSpacing = 1.sp,
    color = TextMuted,
)

// Badge text
val BadgeTextStyle = TextStyle(
    fontFamily = SpaceMonoFont,
    fontWeight = FontWeight.Bold,
    fontSize = 10.sp,
    letterSpacing = 0.5.sp,
)

// Nav item label
val NavLabelStyle = TextStyle(
    fontFamily = SpaceMonoFont,
    fontWeight = FontWeight.SemiBold,
    fontSize = 9.sp,
    letterSpacing = 1.sp,
)
