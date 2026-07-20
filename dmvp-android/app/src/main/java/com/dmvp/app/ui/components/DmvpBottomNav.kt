/**
 * app/src/main/java/com/dmvp/app/ui/components/DmvpBottomNav.kt
 *
 * UDOVP V2 — Bottom Navigation Bar (cyberpunk/terminal aesthetic)
 * Follows docs/ui-v2.html .bnav design
 *
 * PR 3: Dashboard + Bottom Navigation
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmvp.app.ui.theme.*

/**
 * Bottom navigation item data
 */
data class BottomNavItem(
    val route: String,
    val icon: String,
    val label: String,
)

/**
 * UDOVP V2 Bottom Navigation Bar
 *
 * 5 tabs: Home, Register, Verify, Compare, Assets
 * Active tab: cyan color with cyan background tint
 * Inactive: muted color
 */
@Composable
fun DmvpBottomNav(
    currentRoute: String?,
    onItemSelected: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        BottomNavItem(route = "home", icon = "\uD83C\uDFE0", label = "Home"),
        BottomNavItem(route = "register", icon = "\uD83D\uDCE4", label = "Register"),
        BottomNavItem(route = "verify", icon = "\uD83D\uDEE1\uFE0F", label = "Verify"),
        BottomNavItem(route = "compare", icon = "\u2696\uFE0F", label = "Compare"),
        BottomNavItem(route = "assets", icon = "\uD83D\uDCDC", label = "Assets"),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(78.dp)
            .background(BgBase.copy(alpha = 0.92f))
            .border(1.dp, BorderDefault),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val isActive = currentRoute == item.route
            val textColor = if (isActive) CyanPrimary else TextMuted
            val bgColor = if (isActive) CyanPrimary.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent

            Column(
                modifier = Modifier
                    .clickable { onItemSelected(item) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .background(bgColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Icon
                Text(
                    text = item.icon,
                    fontSize = 18.sp,
                )
                // Label
                Text(
                    text = item.label.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}
