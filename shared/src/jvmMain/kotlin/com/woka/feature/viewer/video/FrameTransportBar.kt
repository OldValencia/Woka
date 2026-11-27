package com.woka.feature.viewer.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.testTag
import com.woka.core.data.frameDelayMs
import com.woka.core.ui.components.frames.FrameTransport
import com.woka.core.ui.components.frames.durationMs
import com.woka.core.ui.components.frames.playable
import com.woka.core.ui.components.frames.playablePosition
import com.woka.core.ui.components.frames.seekTo
import com.woka.core.ui.components.frames.step
import com.woka.core.ui.components.frames.togglePlaying
import com.woka.core.ui.icons.Icons
import com.woka.feature.viewer.shell.slider.MediaSlider
import com.woka.feature.viewer.shell.slider.SliderPreview
import kotlin.math.roundToInt

/**
 * An animation's transport (RD-501, `#viewer/gif`, `#editor/gif`): the film's box and left
 * group, with an animation's slots — ❙◀ seek ▶❙, the length, Loop and speed.
 *
 * Drawn by the viewer on glass and by the editor on the room's panel ([colors]); both hand
 * it their own [FrameTransport], so the bar moves whichever clock it is shown with.
 * [loadPreview] is the frame's thumbnail for the seek's tooltip.
 */
@Composable
fun FrameTransportBar(
    transport: FrameTransport,
    colors: TransportColors,
    tooltip: TransportTooltip,
    loadPreview: suspend (frameIndex: Int) -> ImageBitmap?,
    modifier: Modifier = Modifier,
    onSpeedHover: (Boolean) -> Unit = {},
    onSeekHover: (Boolean) -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth().transportBar(colors).testTag("frame-transport"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TransportTokens.gap)
    ) {
        val playing = transport.isPlaying
        tooltip(if (playing) "Pause   ·   Space" else "Play   ·   Space") {
            PlayKnob(playing, colors.knob, "frame-transport-play") { transport.togglePlaying() }
        }
        FrameTimeLabel(transport, colors, tooltip)
        tooltip("Previous frame   ·   ,") {
            TransportSlot(Icons.Static.FrameBack, "Previous frame", colors.text, "frame-transport-back") { transport.step(-1) }
        }
        FrameSeek(transport, colors, loadPreview, onSeekHover, Modifier.weight(1f))
        tooltip("Next frame   ·   .") {
            TransportSlot(Icons.Static.FrameForward, "Next frame", colors.text, "frame-transport-forward") { transport.step(1) }
        }
        FrameDurationLabel(transport.durationMs(), colors)
        val loop = transport.loop
        tooltip("Repeat") {
            TransportSlot(
                Icons.Static.Repeat,
                if (loop) "Repeat on" else "Repeat off",
                if (loop) colors.loopOn else colors.loopOff,
                "frame-transport-loop",
                fill = if (loop) colors.loopOnFill else Color.Transparent
            ) { transport.loop = !loop }
        }
        TransportSlot(Icons.Static.Speed, "Playback speed", colors.text, "frame-transport-speed", onHover = onSpeedHover)
    }
}

/**
 * The seek over the timed frames: a drag or a click pauses and shows that frame; the tooltip
 * names the frame as the leading label does, over its thumbnail.
 */
@Composable
private fun FrameSeek(
    transport: FrameTransport,
    colors: TransportColors,
    loadPreview: suspend (Int) -> ImageBitmap?,
    onSeekHover: (Boolean) -> Unit,
    modifier: Modifier
) {
    val walk = transport.playable
    val last = (walk.size - 1).coerceAtLeast(1)
    var hovered by remember(walk) { mutableStateOf<Int?>(null) }
    val bitmap by produceState<ImageBitmap?>(null, hovered) {
        hovered?.let { at -> walk.getOrNull(at)?.let { value = loadPreview(it.index) } }
    }
    val painter = remember(bitmap) { bitmap?.let(::BitmapPainter) }
    MediaSlider(
        value = { transport.playablePosition().toFloat() / last },
        onValueChange = { fraction ->
            walk.getOrNull((fraction * last).roundToInt())?.let { transport.seekTo(it.index) }
        },
        style = colors.slider,
        tooltipText = { fraction -> labelAt(transport, walk.getOrNull((fraction * last).roundToInt())?.index) },
        tooltipPreview = SliderPreview(painter, bitmap?.let { it.width.toFloat() / it.height }),
        onTooltipPositionChange = { fraction ->
            onSeekHover(fraction != null)
            hovered = fraction?.let { (it * last).roundToInt() }
        },
        modifier = modifier.testTag("frame-transport-seek")
    )
}

/** What the leading label would read with [frameIndex] on screen. */
private fun labelAt(transport: FrameTransport, frameIndex: Int?): String {
    val walk = transport.playable
    val at = walk.indexOfFirst { it.index == frameIndex }.coerceAtLeast(0)
    return if (transport.showFrameNumbers) "${at + 1} / ${walk.size}"
    else formatTenths(walk.take(at).sumOf { it.frameDelayMs().toLong() })
}
