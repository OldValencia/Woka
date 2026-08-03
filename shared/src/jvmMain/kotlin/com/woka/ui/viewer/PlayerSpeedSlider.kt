package com.woka.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.woka.data.PlaybackSpeed

private val KNOB_SIZE = 12.dp
private val DETENT_SIZE = 4.dp
private val TRACK_HEIGHT = 3.dp

/**
 * Playback-speed slider (§6): a thin track with a **detent dot at 1×** that the
 * knob snaps onto, so returning to normal speed is a gesture rather than a target
 * you have to hit exactly. Snapping is done by [PlaybackSpeed.snap], shared with
 * the persistence layer so a stored value can never sit just off 1×.
 */
@Composable
internal fun PlayerSpeedSlider(speed: Float, onSpeedChange: (Float) -> Unit) {
    var trackWidthPx by remember { mutableStateOf(0) }
    // Live drag value. The knob follows the finger, but the *player* only hears
    // the final one: every committed change re-seeks the audio grabber and
    // rebuilds the atempo graph, and a drag emits a dozen of those (§6).
    var dragSpeed by remember { mutableStateOf<Float?>(null) }
    val density = LocalDensity.current
    val knobPx = with(density) { KNOB_SIZE.toPx() }

    val shown = dragSpeed ?: speed

    fun commitDrag() {
        dragSpeed?.let(onSpeedChange)
        dragSpeed = null
    }

    val fraction = (shown - PlaybackSpeed.MIN) / (PlaybackSpeed.MAX - PlaybackSpeed.MIN)
    val normalFraction = (PlaybackSpeed.NORMAL - PlaybackSpeed.MIN) /
        (PlaybackSpeed.MAX - PlaybackSpeed.MIN)

    fun speedAt(x: Float): Float {
        val usable = (trackWidthPx - knobPx).coerceAtLeast(1f)
        val ratio = ((x - knobPx / 2f) / usable).coerceIn(0f, 1f)
        return PlaybackSpeed.snap(
            PlaybackSpeed.MIN + ratio * (PlaybackSpeed.MAX - PlaybackSpeed.MIN)
        )
    }

    PlayerSpeedHeader(shown)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .onSizeChanged { trackWidthPx = it.width }
            .pointerInput(trackWidthPx) {
                detectTapGestures { onSpeedChange(speedAt(it.x)) }
            }
            .pointerInput(trackWidthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { dragSpeed = speedAt(it.x) },
                    onDragEnd = { commitDrag() },
                    onDragCancel = { commitDrag() }
                ) { change, _ ->
                    dragSpeed = speedAt(change.position.x)
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TRACK_HEIGHT)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.15f))
        )
        // The 1x detent, drawn under the knob so the knob covers it when parked.
        val dotPx = with(density) { DETENT_SIZE.toPx() }
        Box(
            modifier = Modifier
                .offset { IntOffset(markerLeft(trackWidthPx, knobPx, normalFraction, dotPx), 0) }
                .size(DETENT_SIZE)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.45f))
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(markerLeft(trackWidthPx, knobPx, fraction, knobPx), 0) }
                .size(KNOB_SIZE)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
