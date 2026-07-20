/**
 * app/src/main/java/com/dmvp/app/ui/theme/Color.kt
 *
 * UDOVP V2 Design System — Cyberpunk/terminal aesthetic
 * Primary: Cyan (#00FFB4) — action, success, highlights
 * Secondary: Purple (#A78BFA) — secondary actions, accents
 * Background: Near-black (#08090D) with subtle gradients
 *
 * Reference: docs/ui-v2.html
 */

package com.dmvp.app.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════
// Primary Palette — Cyan
// ═══════════════════════════════════════════════════════
val CyanPrimary = Color(0xFF00FFB4)
val CyanDim = Color(0xFF00B386)
val CyanMuted = Color(0xFF00E5A0)

// ═══════════════════════════════════════════════════════
// Secondary Palette — Purple
// ═══════════════════════════════════════════════════════
val PurplePrimary = Color(0xFFA78BFA)
val PurpleDeep = Color(0xFF7C3AED)
val PurpleMuted = Color(0xFF8B5CF6)

// ═══════════════════════════════════════════════════════
// Accent Colors
// ═══════════════════════════════════════════════════════
val PinkAccent = Color(0xFFF472B6)
val AmberAccent = Color(0xFFFBBF24)
val RedAccent = Color(0xFFEF4444)

// ═══════════════════════════════════════════════════════
// Background & Surface — Dark Theme
// ═══════════════════════════════════════════════════════
val BgBase = Color(0xFF08090D)
val SurfaceGlass = Color(0xFF0E1018)
val SurfaceCard = Color(0xFF141826)
val SurfaceElevated = Color(0xFF1A1F2E)

// ═══════════════════════════════════════════════════════
// Border & Divider
// ═══════════════════════════════════════════════════════
val BorderDefault = Color(0x0FFFFFFF)
val BorderHighlight = Color(0x4000FFB4)
val DividerSubtle = Color(0x10FFFFFF)

// ═══════════════════════════════════════════════════════
// Text Colors
// ═══════════════════════════════════════════════════════
val TextPrimary = Color(0xFFE2E8F0)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF4A5568)
val TextDim = Color(0xFF2D3748)

// ═══════════════════════════════════════════════════════
// Status / Feedback
// ═══════════════════════════════════════════════════════
val StatusSuccess = Color(0xFF00FFB4)
val StatusWarning = Color(0xFFFBBF24)
val StatusError = Color(0xFFEF4444)
val StatusInfo = Color(0xFFA78BFA)

// ═══════════════════════════════════════════════════════
// Badge Backgrounds (with 10-15% opacity)
// ═══════════════════════════════════════════════════════
val BadgeSuccessBg = Color(0x1A00FFB4)
val BadgeWarningBg = Color(0x1AFBBF24)
val BadgeErrorBg = Color(0x1AEF4444)
val BadgeInfoBg = Color(0x1AA78BFA)

// ═══════════════════════════════════════════════════════
// Trust Tier Colors
// ═══════════════════════════════════════════════════════
val TierA = Color(0xFF00FFB4)
val TierB = Color(0xFFA78BFA)
val TierC = Color(0xFFFBBF24)
val TierD = Color(0xFFEF4444)

// ═══════════════════════════════════════════════════════
// Gradient Colors
// ═══════════════════════════════════════════════════════
val GradientStart = Color(0xFF00FFB4)
val GradientEnd = Color(0xFFA78BFA)
val GradientButton = listOf(Color(0xFF00FFB4), Color(0xFF00B386))
val GradientAccent = listOf(Color(0xFF00FFB4), Color(0xFFA78BFA))

// ═══════════════════════════════════════════════════════
// Backward-compatible aliases (for existing screens)
// These will be removed once all screens migrate to V2 components
// ═══════════════════════════════════════════════════════
@Deprecated("Use CyanPrimary instead") val CyanBright = CyanPrimary
@Deprecated("Use PurpleDeep instead") val DeepPurple900 = PurpleDeep
@Deprecated("Use PurpleDeep instead") val DeepPurple800 = PurpleDeep
@Deprecated("Use PurplePrimary instead") val DeepPurple700 = PurplePrimary
@Deprecated("Use PurpleMuted instead") val DeepPurple600 = PurpleMuted
@Deprecated("Use PurplePrimary instead") val DeepPurple500 = PurplePrimary
@Deprecated("Use PurplePrimary instead") val DeepPurple400 = PurplePrimary
@Deprecated("Use PurplePrimary instead") val DeepPurple200 = PurplePrimary
@Deprecated("Use PurplePrimary instead") val DeepPurple100 = PurplePrimary
@Deprecated("Use SurfaceCard instead") val DarkSurface = SurfaceCard
@Deprecated("Use SurfaceElevated instead") val DarkSurfaceVariant = SurfaceElevated
@Deprecated("Use SurfaceElevated instead") val DarkSurfaceContainer = SurfaceElevated
@Deprecated("Use SurfaceElevated instead") val DarkSurfaceContainerHigh = SurfaceElevated
@Deprecated("Use TextPrimary instead") val TextPrimaryDark = TextPrimary
@Deprecated("Use TextSecondary instead") val TextSecondaryDark = TextSecondary
@Deprecated("Use TextMuted instead") val TextTertiaryDark = TextMuted
@Deprecated("Use BgBase instead") val DarkBackground = BgBase
@Deprecated("Use BorderDefault instead") val DividerDark = BorderDefault
@Deprecated("Use StatusSuccess instead") val Success = StatusSuccess
@Deprecated("Use StatusWarning instead") val Warning = StatusWarning
@Deprecated("Use StatusError instead") val Error = StatusError
@Deprecated("Use StatusInfo instead") val Info = StatusInfo
@Deprecated("Use TierA instead") val TierABadge = TierA
@Deprecated("Use TierB instead") val TierBBadge = TierB
@Deprecated("Use TierC instead") val TierCBadge = TierC
@Deprecated("Use TierD instead") val TierDBadge = TierD
