package io.iris.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.iris.ui.theme.IrisTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun DropdownItem(
    text: String,
    enabled: Boolean = true,
    textColor: Color? = null,
    iconRes: DrawableResource? = null,
    onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val isHovered by src.collectIsHoveredAsState()
    val bg by animateColorAsState(if (isHovered && enabled) IrisTheme.colors.surfaceHover else Color.Transparent)
    val fg = if (!enabled) IrisTheme.colors.textTertiary else textColor ?: IrisTheme.colors.textPrimary
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(
                enabled = enabled,
                interactionSource = src,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(fg)
            )
        }
        BasicText(
            text = text,
            style = TextStyle(color = fg, fontSize = 13.sp)
        )
    }
}
