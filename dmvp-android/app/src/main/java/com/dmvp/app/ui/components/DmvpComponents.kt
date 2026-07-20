/**
 * app/src/main/java/com/dmvp/app/ui/components/DmvpComponents.kt
 *
 * UDOVP V2 Design System — Reusable Components
 * All components follow the cyberpunk/terminal aesthetic from docs/ui-v2.html
 *
 * Components:
 * - DmvpCard: Glass-morphism card container
 * - DmvpButton: Primary, secondary, danger buttons
 * - DmvpBadge: Status badges (ok, warn, err, info)
 * - DmvpInput: Text input with focus glow
 * - DmvpSwitch: Toggle switch with cyan active state
 * - DmvpStatBox: Dashboard stat display
 * - DmvpActionTile: Quick action grid item
 * - DmvpAssetRow: Asset list item
 * - DmvpCertRow: Certificate detail row
 * - DmvpProgressBar: Gradient progress bar
 * - DmvpTrustCircle: Circular trust score display
 * - DmvpBottomNav: Bottom navigation bar
 */

package com.dmvp.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmvp.app.ui.theme.*

// ═══════════════════════════════════════════════════════
// DmvpCard — Glass-morphism card
// ═══════════════════════════════════════════════════════

@Composable
fun DmvpCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderColor = if (glow) BorderHighlight else BorderDefault
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}

// ═══════════════════════════════════════════════════════
// DmvpButton — Primary / Secondary / Danger
// ═══════════════════════════════════════════════════════

enum class ButtonVariant { PRIMARY, SECONDARY, DANGER }

@Composable
fun DmvpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = when (variant) {
        ButtonVariant.PRIMARY -> ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.Black,
            disabledContainerColor = CyanPrimary.copy(alpha = 0.3f),
        )
        ButtonVariant.SECONDARY -> ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = TextPrimary,
            disabledContainerColor = BorderDefault,
        )
        ButtonVariant.DANGER -> ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = StatusError,
            disabledContainerColor = StatusError.copy(alpha = 0.1f),
        )
    }

    val borderMod = when (variant) {
        ButtonVariant.PRIMARY -> Modifier
        ButtonVariant.SECONDARY -> Modifier.border(1.dp, BorderDefault, RoundedCornerShape(14.dp))
        ButtonVariant.DANGER -> Modifier.border(1.dp, StatusError.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
    }

    val bgMod = if (variant == ButtonVariant.PRIMARY) {
        Modifier.background(
            brush = Brush.linearGradient(GradientButton),
            RoundedCornerShape(14.dp)
        )
    } else {
        Modifier.background(
            when (variant) {
                ButtonVariant.SECONDARY -> Color.White.copy(alpha = 0.04f)
                ButtonVariant.DANGER -> StatusError.copy(alpha = 0.08f)
                else -> Color.Transparent
            },
            RoundedCornerShape(14.dp)
        )
    }

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(borderMod)
            .then(bgMod),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = colors,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 0.5.sp,
        )
    }
}

// ═══════════════════════════════════════════════════════
// DmvpBadge — Status badges
// ═══════════════════════════════════════════════════════

enum class BadgeVariant { OK, WARN, ERR, INFO }

@Composable
fun DmvpBadge(
    text: String,
    variant: BadgeVariant = BadgeVariant.OK,
    modifier: Modifier = Modifier,
) {
    val (bgColor, textColor, borderColor) = when (variant) {
        BadgeVariant.OK -> Triple(BadgeSuccessBg, StatusSuccess, StatusSuccess.copy(alpha = 0.2f))
        BadgeVariant.WARN -> Triple(BadgeWarningBg, StatusWarning, StatusWarning.copy(alpha = 0.2f))
        BadgeVariant.ERR -> Triple(BadgeErrorBg, StatusError, StatusError.copy(alpha = 0.2f))
        BadgeVariant.INFO -> Triple(BadgeInfoBg, StatusInfo, StatusInfo.copy(alpha = 0.2f))
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = bgColor,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            style = BadgeTextStyle,
            color = textColor,
        )
    }
}

// ═══════════════════════════════════════════════════════
// DmvpInput — Text input with cyan focus glow
// ═══════════════════════════════════════════════════════

@Composable
fun DmvpInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isPassword: Boolean = false,
    singleLine: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        if (focused) CyanPrimary else BorderDefault, label = "inputBorder"
    )

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            ),
        placeholder = { Text(placeholder, color = TextDim) },
        textStyle = TextStyle(
            color = TextPrimary,
            fontSize = 14.sp,
            fontFamily = OutfitFont,
        ),
        singleLine = singleLine,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = CyanPrimary,
            focusedContainerColor = Color.Black.copy(alpha = 0.35f),
            unfocusedContainerColor = Color.Black.copy(alpha = 0.35f),
        ),
        visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
    )
}

// ═══════════════════════════════════════════════════════
// DmvpSwitch — Toggle with cyan active state
// ═══════════════════════════════════════════════════════

@Composable
fun DmvpSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.Black,
            checkedTrackColor = CyanPrimary,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = TextDim,
        ),
    )
}

// ═══════════════════════════════════════════════════════
// DmvpStatBox — Dashboard stat
// ═══════════════════════════════════════════════════════

@Composable
fun DmvpStatBox(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary,
    accentColor: Color = CyanPrimary,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, BorderDefault, RoundedCornerShape(8.dp))
            .background(SurfaceCard, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accentColor, RoundedCornerShape(2.dp))
                .align(Alignment.CenterStart)
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(value, style = StatValueStyle, color = valueColor)
            Spacer(Modifier.height(6.dp))
            Text(label.uppercase(), style = StatLabelStyle)
        }
    }
}

// ═══════════════════════════════════════════════════════
// DmvpActionTile — Quick action grid item
// ═══════════════════════════════════════════════════════

@Composable
fun DmvpActionTile(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val borderColor = if (active) CyanPrimary else BorderDefault
    val bgColor = if (active) CyanPrimary.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.025f)

    Column(
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(bgColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            label.uppercase(),
            style = TextStyle(
                fontFamily = SpaceMonoFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
                color = TextPrimary,
            ),
        )
    }
}

// ═══════════════════════════════════════════════════════
// DmvpAssetRow — Asset list item
// ═══════════════════════════════════════════════════════

@Composable
fun DmvpAssetRow(
    icon: String,
    name: String,
    meta: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    badgeVariant: BadgeVariant = BadgeVariant.OK,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            )
            .border(1.dp, BorderDefault, RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(CyanPrimary.copy(alpha = 0.06f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, fontSize = 18.sp)
        }
        Spacer(Modifier.width(14.dp))
        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = TextStyle(
                    fontFamily = OutfitFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = TextPrimary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                meta,
                style = TextStyle(
                    fontFamily = SpaceMonoFont,
                    fontSize = 11.sp,
                    color = TextMuted,
                ),
                maxLines = 1,
            )
        }
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            DmvpBadge(text = badge, variant = badgeVariant)
        }
    }
}

// ═══════════════════════════════════════════════════════
// DmvpCertRow — Certificate detail row
// ═══════════════════════════════════════════════════════

@Composable
fun DmvpCertRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary,
    isLast: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (!isLast) Modifier.padding(bottom = 10.dp)
                    .border(
                        width = 0.dp,
                        shape = RoundedCornerShape(0.dp),
                        color = Color.Transparent
                    )
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = TextStyle(fontSize = 13.sp, color = TextMuted),
            )
            Text(
                value,
                style = TextStyle(
                    fontFamily = SpaceMonoFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = valueColor,
                ),
                textAlign = TextAlign.End,
            )
        }
        if (!isLast) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(
                thickness = 1.dp,
                color = DividerSubtle,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// DmvpProgressBar — Gradient progress bar
// ═══════════════════════════════════════════════════════

@Composable
fun DmvpProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.06f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                .background(
                    Brush.horizontalGradient(GradientAccent),
                    RoundedCornerShape(3.dp),
                )
        )
    }
}

// ═══════════════════════════════════════════════════════
// DmvpTrustCircle — Circular trust score
// ═══════════════════════════════════════════════════════

@Composable
fun DmvpTrustCircle(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .border(3.dp, CyanPrimary.copy(alpha = 0.15f), CircleShape)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${score}%",
            style = TextStyle(
                fontFamily = SpaceMonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = CyanPrimary,
            ),
        )
    }
}

// ═══════════════════════════════════════════════════════
// DmvpSectionHeader — "QUICK ACTIONS" style header
// ═══════════════════════════════════════════════════════

@Composable
fun DmvpSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(bottom = 12.dp),
        style = SectionHeaderStyle,
    )
}
