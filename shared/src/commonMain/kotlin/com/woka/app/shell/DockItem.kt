package com.woka.app.shell

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
import androidx.compose.ui.draw.shadow
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
fun DockItem(
    text: String,
    iconRes: DrawableResource,
    isActive: Boolean,
    activeColor: Color,
    hasBadge: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit
) {
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor by animateColorAsState(
        targetValue = when {
            isActive -> Theme.colors.surfacePressed
            isHovered -> Theme.colors.surfaceHover
            else -> Color.Transparent
        },
        animationSpec = tween(150)
    )
    val textColor by animateColorAsState(
        targetValue = if (isActive) Theme.colors.textPrimary else if (isHovered) Theme.colors.textPrimary else Theme.colors.textTertiary,
        animationSpec = tween(150)
    )
    val iconTint by animateColorAsState(
        targetValue = if (isActive) activeColor else textColor,
        animationSpec = tween(150)
    )

    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box {
            Image(
                painter = painterResource(iconRes),
                contentDescription = text,
                modifier = Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(iconTint)
            )
            if (hasBadge) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(activeColor)
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-3).dp)
                        .shadow(4.dp, RoundedCornerShape(3.dp), spotColor = activeColor)
                )
            }
        }
        BasicText(
            text = text,
            style = TextStyle(color = textColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}
