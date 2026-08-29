package com.woka.feature.viewer.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Where the boost zone starts and ends — the HIG's own green and red. */
private val BOOST_START = Color(0xFF34C759)
private val BOOST_END = Color(0xFFFF3B30)

/**
 * The volume slider's active bar: white up to 100 %, green-to-red above it (Sound boost).
 *
 * **The ramp is anchored to the track, not to the bar.** [boostColor] reads the colour off
 * the level's place in the *whole* boost zone, so 100 % is green and the ceiling is red
 * however far the bar currently reaches — a gradient drawn across the bar itself would
 * stretch, and the same colour would mean 150 % one moment and 280 % the next.
 *
 * Two segments in a bottom-aligned column rather than one gradient with a transparent
 * half: the boundary is a *level*, and expressing it as weights is exact where a gradient
 * stop would have to be recomputed from the bar's own height every frame.
 *
 * [fraction] is the bar's share of the track — the slider's own units — and [ceiling] the
 * top of the range, so 100 % sits at `1 / ceiling` of the way up.
 */
@Composable
internal fun VolumeSliderFill(fraction: Float, ceiling: Float) {
    val filled = fraction.coerceIn(0f, 1f)
    if (filled <= 0f) return

    val unity = (1f / ceiling).coerceIn(0f, 1f)
    val boost = (filled - unity).coerceAtLeast(0f)
    Column(
        verticalArrangement = Arrangement.Bottom,
        // Clipped once, around both: clipping each segment would pinch the joint
        // between them into an hourglass.
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(filled)
            .clip(CircleShape)
    ) {
        if (boost > 0f) {
            Segment(
                weight = boost,
                brush = Brush.verticalGradient(
                    listOf(boostColor(filled, unity), BOOST_START)
                )
            )
        }
        val plain = filled - boost
        if (plain > 0f) Segment(weight = plain, brush = Brush.verticalGradient(WHITE_BAR))
    }
}

/**
 * The colour [volume] has reached on the same ramp, for the percentage above the slider —
 * null at or below 100 %, where the reading is the ordinary white one.
 */
internal fun boostLabelColor(volume: Float, ceiling: Float): Color? {
    if (volume <= 1f || ceiling <= 1f) return null
    val unity = (1f / ceiling).coerceIn(0f, 1f)
    return boostColor((volume / ceiling).coerceAtMost(1f), unity)
}

private fun boostColor(fraction: Float, unity: Float): Color {
    val travelled = ((fraction - unity) / (1f - unity)).coerceIn(0f, 1f)
    return lerp(BOOST_START, BOOST_END, travelled)
}

private val WHITE_BAR = listOf(Color.White, Color.White)

@Composable
private fun ColumnScope.Segment(weight: Float, brush: Brush) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(weight)
            .background(brush)
    )
}
