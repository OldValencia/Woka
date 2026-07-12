package io.iris.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.iris.ui.theme.IrisTheme

@Composable
fun DropdownItem(text: String, enabled: Boolean = true, textColor: Color? = null, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val isHovered by src.collectIsHoveredAsState()
    val bg by animateColorAsState(if (isHovered && enabled) IrisTheme.colors.surfaceHover else Color.Transparent)
    val fg = if (!enabled) IrisTheme.colors.textTertiary else textColor ?: IrisTheme.colors.textPrimary
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(
                enabled = enabled,
                interactionSource = src,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        BasicText(
            text = text,
            style = TextStyle(color = fg, fontSize = 13.sp)
        )
    }
}
