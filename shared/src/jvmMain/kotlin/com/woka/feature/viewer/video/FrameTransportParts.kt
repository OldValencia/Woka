package com.woka.feature.viewer.video

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.woka.core.ui.components.frames.FrameTransport
import com.woka.core.ui.components.frames.playable
import com.woka.core.ui.components.frames.playablePosition
import com.woka.core.ui.components.frames.positionMs
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** A tooltip around a slot: the viewer's and the room's are different components. */
typealias TransportTooltip = @Composable (text: String, content: @Composable () -> Unit) -> Unit

private val SLOT_SHAPE = RoundedCornerShape(7.dp)

/** `n / N` among the timed frames, or the time at which the frame on screen appears. */
internal fun FrameTransport.leadingLabel(): String =
    if (showFrameNumbers) "${playablePosition() + 1} / ${playable.size}" else formatTenths(positionMs())

/**
 * The leading label (`#viewer-time` as `frameTime`): reads the clock in its own scope, so a
 * running GIF recomposes this text and nothing else in the bar. A click switches it between
 * time and frame number.
 */
@Composable
internal fun FrameTimeLabel(transport: FrameTransport, colors: TransportColors, tooltip: TransportTooltip) {
    tooltip("Click to switch between time and frame number.") {
        BasicText(
            text = transport.leadingLabel(),
            modifier = Modifier
                .widthIn(min = TransportTokens.timeMinWidth)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    transport.showFrameNumbers = !transport.showFrameNumbers
                }
                .testTag("frame-transport-time"),
            style = timeLabelStyle().copy(color = colors.text),
            maxLines = 1
        )
    }
}

/**
 * One 28 dp slot with an 18 dp glyph — the step buttons, Loop and speed. [fill] is the
 * pressed-on background Loop takes; [onHover] is how the speed slot opens its popover,
 * by hover as ⚙ does.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TransportSlot(
    icon: DrawableResource,
    description: String,
    tint: Color,
    tag: String,
    fill: Color = Color.Transparent,
    onHover: (Boolean) -> Unit = {},
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(TransportTokens.button)
            .clip(SLOT_SHAPE)
            .background(fill)
            .pointerHoverIcon(PointerIcon.Hand)
            .onPointerEvent(PointerEventType.Enter) { onHover(true) }
            .onPointerEvent(PointerEventType.Exit) { onHover(false) }
            .then(
                if (onClick == null) Modifier
                else Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            )
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(TransportTokens.glyph),
            colorFilter = ColorFilter.tint(tint)
        )
    }
}

/** The trailing label: one pass at 1×, not clickable (`frameDuration`). */
@Composable
internal fun FrameDurationLabel(durationMs: Long, colors: TransportColors) {
    BasicText(formatTenths(durationMs), style = timeLabelStyle().copy(color = colors.text), maxLines = 1)
}
