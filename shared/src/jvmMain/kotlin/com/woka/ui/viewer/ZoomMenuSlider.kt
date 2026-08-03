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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.woka.data.ScalePreset
import com.woka.ui.theme.Theme

private val KNOB_SIZE = 12.dp
private val DETENT_SIZE = 4.dp
private val TRACK_HEIGHT = 3.dp

/**
 * Zoom track. **Logarithmic** — on a linear track the whole useful region below
 * 100% collapses into a tenth of the travel. Fit / Fill / Actual are drawn as
 * detent dots at their current percentages, the same visual language as the 1×
 * detent in [PlayerSpeedSlider].
 */
@Composable
internal fun ZoomMenuSlider(
    percent: Int,
    bounds: IntRange,
    container: IntSize,
    native: IntSize,
    onPercent: (Int) -> Unit
) {
    var trackWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val knobPx = with(density) { KNOB_SIZE.toPx() }
    val dotPx = with(density) { DETENT_SIZE.toPx() }

    fun percentAt(x: Float): Int {
        val usable = (trackWidthPx - knobPx).coerceAtLeast(1f)
        val fraction = ((x - knobPx / 2f) / usable).coerceIn(0f, 1f)
        return percentFromFraction(fraction, bounds)
    }

    val fraction = sliderFraction(percent, bounds)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .onSizeChanged { trackWidthPx = it.width }
            .pointerInput(trackWidthPx, bounds) {
                detectTapGestures { onPercent(percentAt(it.x)) }
            }
            .pointerInput(trackWidthPx, bounds) {
                detectHorizontalDragGestures { change, _ -> onPercent(percentAt(change.position.x)) }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TRACK_HEIGHT)
                .clip(RoundedCornerShape(2.dp))
                .background(Theme.colors.border)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(TRACK_HEIGHT)
                .clip(RoundedCornerShape(2.dp))
                .background(Theme.colors.textPrimary.copy(alpha = 0.55f))
        )
        listOf(ScalePreset.Fit, ScalePreset.Fill, ScalePreset.Original).forEach { preset ->
            val detent = sliderFraction(presetPercent(preset, container, native), bounds)
            Box(
                modifier = Modifier
                    .offset { IntOffset(markerLeft(trackWidthPx, knobPx, detent, dotPx), 0) }
                    .size(DETENT_SIZE)
                    .clip(CircleShape)
                    .background(Theme.colors.textSecondary.copy(alpha = 0.55f))
            )
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(markerLeft(trackWidthPx, knobPx, fraction, knobPx), 0) }
                .size(KNOB_SIZE)
                .clip(CircleShape)
                .background(Theme.colors.textPrimary)
        )
    }
}
