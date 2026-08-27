package com.woka.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource

/**
 * The settings card's close button — the only thing in this package with an
 * animation of its own.
 *
 * Moved out of `SettingsComponents.kt` when ME-115 added [PanelHint] there and the
 * file would have passed 150 lines. The seam is the one that was already implicit:
 * `SidebarItem`, `SectionLabel` and `PanelHint` are the panels' vocabulary, while
 * this is one chrome control with three animated values and a rotation nothing else
 * in the package uses.
 *
 * `contentDescription` is set — unusually for an icon here — because it is the one
 * anchor `SettingsOverlayUiTest` has for the button: it draws no text.
 */
@Composable
fun CloseIconButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val backgroundColor by animateColorAsState(
        targetValue = if (isHovered) Theme.colors.surfaceHover else Color.Transparent,
        animationSpec = tween(140)
    )
    val rotation by animateFloatAsState(
        targetValue = if (isHovered) 90f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f)
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f)
    )

    Box(
        modifier = Modifier
            .size(28.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(Icons.Static.Close),
            contentDescription = "Close settings",
            modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = rotation },
            colorFilter = ColorFilter.tint(Theme.colors.textSecondary)
        )
    }
}
