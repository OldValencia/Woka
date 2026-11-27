package com.woka.feature.viewer.video

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import org.jetbrains.compose.resources.painterResource

/** `.transport-play` (`base.css`): one 32 dp circle, a 15 dp glyph. */
private val KNOB_SIZE = 32.dp
private val KNOB_GLYPH = 15.dp

/** The knob's fill, its fill under the pointer, and the glyph. */
@Immutable
data class PlayKnobColors(val fill: Color, val hover: Color, val glyph: Color)

/** Over the viewer's media: the raised surface, the app's hover (`button:hover`). */
@Composable
fun viewerPlayKnob(): PlayKnobColors =
    PlayKnobColors(Theme.colors.surfaceRaised, Theme.colors.surfaceHover, Theme.colors.textPrimary)

/** In the editor's room: the raised well, the knob colour under the pointer. */
@Composable
fun roomPlayKnob(): PlayKnobColors =
    PlayKnobColors(Theme.room.well2, Theme.room.knob, Theme.room.text)

/**
 * The transport's play button — one geometry for the viewer's bar, a GIF's bar in the editor
 * and the film's timeline (PT-002, RD-501). The caller wraps it in its own tooltip, because
 * the viewer's and the room's tooltips are different components.
 */
@Composable
fun PlayKnob(
    playing: Boolean,
    colors: PlayKnobColors,
    tag: String,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Box(
        modifier
            .size(KNOB_SIZE)
            .clip(CircleShape)
            .background(if (hovered) colors.hover else colors.fill)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = hover, indication = null, onClick = onToggle)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(if (playing) Icons.Static.Pause else Icons.Static.PlayFill),
            contentDescription = if (playing) "Pause" else "Play",
            modifier = Modifier.size(KNOB_GLYPH),
            colorFilter = ColorFilter.tint(colors.glyph)
        )
    }
}
