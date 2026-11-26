package com.woka.feature.editor.engine.video

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.ViewerTooltip
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

const val PLAY_HEAD_SPLIT_TAG = "editor-strip-split"

/** `.playhead-split` fades and shrinks over 140 ms when there is nowhere to cut. */
private const val FADE_MS = 140

/**
 * The scissors on the play head (`.playhead-split`): 26 × 22 on the ruler, centred on the line,
 * dark on the room's orange — white on `#FF9F0A` measures 2.06 : 1 (ME-102). While playing, or
 * with the play head on a join or a still, they fade to nothing and take no click; the ruler
 * under them is then the ruler again.
 */
@Composable
internal fun BoxScope.Scissors(x: () -> Float, enabled: Boolean, onClick: () -> Unit) {
    val shown by animateFloatAsState(if (enabled) 1f else 0f, tween(FADE_MS))
    if (!enabled && shown == 0f) return
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val shape = RoundedCornerShape(7.dp)
    ViewerTooltip(
        "Split here   ·   S",
        Modifier
            .align(Alignment.TopStart)
            .offset {
                IntOffset((x() - TimelineTokens.scissorsWidth.toPx() / 2f).roundToInt(), TimelineTokens.scissorsTop.roundToPx())
            }
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    alpha = shown
                    scaleX = 0.6f + 0.4f * shown
                    scaleY = 0.6f + 0.4f * shown
                }
                .size(TimelineTokens.scissorsWidth, TimelineTokens.scissorsHeight)
                .shadow(4.dp, shape, spotColor = Color.Black.copy(alpha = 0.33f))
                .background(if (hovered) Theme.room.accentHover else Theme.room.accent, shape)
                .then(
                    if (enabled) {
                        Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(hover, indication = null, onClick = onClick)
                    } else {
                        Modifier
                    }
                )
                .testTag(PLAY_HEAD_SPLIT_TAG),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(Icons.Static.Trim),
                contentDescription = "Split here",
                modifier = Modifier.size(TimelineTokens.scissorsGlyph),
                colorFilter = ColorFilter.tint(Theme.room.onAccent)
            )
        }
    }
}
