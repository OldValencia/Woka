package com.woka.core.ui.components

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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val KNOB_SIZE = 12.dp
private val DETENT_SIZE = 4.dp
private val TRACK_HEIGHT = 3.dp

/** Taller than the visuals so the track is comfortably clickable. */
private val HIT_HEIGHT = 24.dp

@Immutable
data class DetentSliderColors(
    val track: Color,
    val detent: Color,
    val knob: Color,
    /** Null draws no filled portion — the speed track shows position by knob alone. */
    val fill: Color? = null
)

/**
 * A thin horizontal track with snap-dots and a round knob.
 *
 * Zoom and playback speed were two copies of this: same knob, dot and track sizes
 * (declared twice, identically), same positioning maths, same gesture wiring —
 * differing only in range, colours and whether a filled portion is drawn. The
 * copies also put the shared `markerLeft` helper in the *video* package, which the
 * zoom menu then had to import; folding it in here breaks that dependency.
 *
 * Everything is expressed as a 0f..1f [fraction]: the widget knows nothing about
 * percentages or multipliers, and the caller does its own mapping both ways.
 */
@Composable
fun DetentSlider(
    fraction: Float,
    colors: DetentSliderColors,
    onTap: (Float) -> Unit,
    modifier: Modifier = Modifier,
    detents: List<Float> = emptyList(),
    /** Extra `pointerInput` key for callers whose mapping depends on more than width. */
    gestureKey: Any? = null,
    /** Track thickness; the hit area is unchanged. Why a caller thins it is the caller's. */
    trackHeight: Dp = TRACK_HEIGHT,
    onDragStart: ((Float) -> Unit)? = null,
    onDrag: (Float) -> Unit = onTap,
    onDragEnd: () -> Unit = {}
) {
    var trackWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val knobPx = with(density) { KNOB_SIZE.toPx() }
    val dotPx = with(density) { DETENT_SIZE.toPx() }

    fun fractionAt(x: Float): Float {
        val usable = (trackWidthPx - knobPx).coerceAtLeast(1f)
        return ((x - knobPx / 2f) / usable).coerceIn(0f, 1f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HIT_HEIGHT)
            .pointerHoverIcon(PointerIcon.Hand)
            .onSizeChanged { trackWidthPx = it.width }
            .pointerInput(trackWidthPx, gestureKey) {
                detectTapGestures { onTap(fractionAt(it.x)) }
            }
            .pointerInput(trackWidthPx, gestureKey) {
                detectHorizontalDragGestures(
                    onDragStart = { start -> onDragStart?.invoke(fractionAt(start.x)) },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd
                ) { change, _ -> onDrag(fractionAt(change.position.x)) }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.track)
        )
        colors.fill?.let { fillColor ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(fillColor)
            )
        }
        // Dots first, so a knob parked on one covers it rather than sitting behind.
        detents.forEach { detent ->
            Box(
                modifier = Modifier
                    .offset { IntOffset(markerLeft(trackWidthPx, knobPx, detent, dotPx), 0) }
                    .size(DETENT_SIZE)
                    .clip(CircleShape)
                    .background(colors.detent)
            )
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(markerLeft(trackWidthPx, knobPx, fraction, knobPx), 0) }
                .size(KNOB_SIZE)
                .clip(CircleShape)
                .background(colors.knob)
        )
    }
}

/**
 * Left edge of a marker of [markerPx] whose centre sits at [fraction] along the
 * usable track. Knob and dots share this, so a dot lines up under the knob instead
 * of drifting by half the size difference.
 */
private fun markerLeft(trackWidthPx: Int, knobPx: Float, fraction: Float, markerPx: Float): Int {
    val usable = (trackWidthPx - knobPx).coerceAtLeast(0f)
    val centre = knobPx / 2f + fraction * usable
    return (centre - markerPx / 2f).toInt()
}
