package com.woka.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun SidebarItem(
    text: String,
    icon: DrawableResource,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> Theme.colors.textPrimary.copy(alpha = 0.12f)
            isHovered -> Theme.colors.textPrimary.copy(alpha = 0.05f)
            else -> Color.Transparent
        },
        animationSpec = tween(150)
    )
    val contentColor = if (isSelected) Theme.colors.textPrimary else Theme.colors.textSecondary
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(contentColor),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            BasicText(
                text = text,
                style = TextStyle(
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                )
            )
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    BasicText(
        text = text.uppercase(),
        style = TextStyle(
            color = Theme.colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    )
}

/**
 * The one line of explanatory text a panel draws under its [SectionLabel].
 *
 * `private` to `VideoPlayerSettingsPanel` until ME-115 gave the package a second
 * panel that wants the same six lines. The rejected alternative was a second copy
 * of them in `EditorSettingsPanel`, which is how two panels on one card end up
 * drawing their hints at different sizes — the pair is only ever looked at one at a
 * time, so nothing would have caught the drift.
 */
@Composable
fun PanelHint(text: String) {
    BasicText(
        text = text,
        style = TextStyle(color = Theme.colors.textSecondary, fontSize = 11.sp)
    )
}
