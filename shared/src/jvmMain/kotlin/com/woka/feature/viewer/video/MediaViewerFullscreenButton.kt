package com.woka.feature.viewer.video

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.SimpleTooltip
import org.jetbrains.compose.resources.painterResource

/**
 * The film's last slot: into fullscreen and out. Split out of `MediaViewerVideoControls` when
 * RD-501 restyled the bar; it dims under the pointer as the play glyph used to.
 */
@Composable
internal fun MediaViewerFullscreenButton(isFullscreen: Boolean, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        when {
            pressed -> 0.5f
            hovered -> 0.8f
            else -> 1f
        }
    )
    SimpleTooltip(if (isFullscreen) "Exit Fullscreen   ·   F" else "Fullscreen   ·   F") {
        Box(
            modifier = Modifier
                .size(TransportTokens.button)
                .clip(RoundedCornerShape(6.dp))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = interaction, indication = null, onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(if (isFullscreen) Icons.Static.Collapse else Icons.Static.Expand),
                contentDescription = "Fullscreen",
                modifier = Modifier.size(TransportTokens.glyph),
                colorFilter = ColorFilter.tint(Theme.colors.textPrimary.copy(alpha = alpha))
            )
        }
    }
}
