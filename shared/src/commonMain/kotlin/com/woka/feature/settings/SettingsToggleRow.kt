package com.woka.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.core.ui.components.SliderSwitch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * A title, a subtitle and a switch — the row every settings panel is built from.
 *
 * [icon] is last and defaults to null because exactly one caller wants it: the
 * editor's tool list (ME-115), where the row carries the same glyph the tool's rail
 * button draws, so the switch and the button it governs are recognisably the same
 * thing. Null leaves the row laid out exactly as it was, which is what keeps the
 * eleven existing call sites untouched.
 */
@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: DrawableResource? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Theme.colors.surfacePressed)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            // 16.dp and tinted `textSecondary`, exactly as `SidebarItem` draws its
            // own: the two are read one above the other on the same card.
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Theme.colors.textSecondary),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = TextStyle(color = Theme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            )
            Spacer(Modifier.height(3.dp))
            BasicText(
                text = subtitle,
                style = TextStyle(color = Theme.colors.textTertiary, fontSize = 11.sp)
            )
        }
        Spacer(Modifier.width(16.dp))
        SliderSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
