package com.woka.app.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Icon-only dock button — the accessories at the dock's edges, as opposed to the
 * labelled [DockItem] tabs. Split from `DockItem.kt` (§11); the two share nothing
 * but the dock they sit in.
 *
 * [isWiggle] shakes the glyph once on hover, used by the settings gear.
 */
@Composable
fun DockSecondaryItem(
    iconRes: DrawableResource,
    hoverColor: Color,
    isWiggle: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor by animateColorAsState(
        targetValue = if (isHovered) Theme.colors.surfaceHover else Color.Transparent,
        animationSpec = tween(150)
    )
    val iconTint by animateColorAsState(
        targetValue = if (isHovered) hoverColor else Theme.colors.textTertiary,
        animationSpec = tween(150)
    )

    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isHovered, isWiggle) {
        if (isHovered && isWiggle) {
            rotation.animateTo(15f, animationSpec = tween(80, easing = LinearOutSlowInEasing))
            rotation.animateTo(-15f, animationSpec = tween(80, easing = LinearOutSlowInEasing))
            rotation.animateTo(15f, animationSpec = tween(80, easing = LinearOutSlowInEasing))
            rotation.animateTo(0f, animationSpec = tween(80, easing = LinearOutSlowInEasing))
        } else {
            rotation.animateTo(0f, animationSpec = spring(stiffness = Spring.StiffnessMedium))
        }
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    rotationZ = rotation.value
                },
            colorFilter = ColorFilter.tint(iconTint)
        )
    }
}
