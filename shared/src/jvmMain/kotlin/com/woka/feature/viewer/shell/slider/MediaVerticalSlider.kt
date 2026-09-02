package com.woka.feature.viewer.shell.slider

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.woka.core.ui.theme.Theme

/**
 * Vertical counterpart of [MediaSlider], used only by the volume overlay. Split
 * out of `MediaSlider.kt` (§2 file-size mandate) — the two share nothing but a
 * visual language.
 *
 * [ceiling] is the top of the range; the bottom is always 0f. It grows to 300 % when
 * Sound boost is on, and the geometry below divides by it so a level is drawn at the
 * same fraction of the track whatever the range currently is.
 *
 * [detent] is a value the knob snaps to from within [DETENT_BAND] of the track — 100 %
 * for the volume slider, so a range that reaches three times as high does not make the
 * ordinary setting harder to land on.
 *
 * [fill] draws the active part, and defaults to the white bar this always had. A slot
 * because Sound boost colours its own zone green-to-red, and a slider is not the place
 * that knows what a colour means.
 */
@Composable
fun MediaVerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Theme.viewer.onScrim,
    /**
     * **Stays a literal, and ME-121's KDoc names this exact line.** The unplayed track is
     * a *fill describing a track*, not a foreground: 20 % white is a value one slider
     * uses, and a palette entry for it would be a token with a single caller.
     */
    inactiveColor: Color = Color.White.copy(alpha = 0.2f),
    ceiling: Float = 1f,
    detent: Float? = null,
    fill: @Composable (Float) -> Unit = { fraction ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction)
                .clip(CircleShape)
                .background(activeColor)
        )
    }
) {
    var dragY by remember { mutableStateOf<Float?>(null) }
    var height by remember { mutableStateOf(0f) }

    val currentOnValueChange by rememberUpdatedState(onValueChange)

    fun at(y: Float): Float = ((1f - (y / height)) * ceiling)
        .coerceIn(0f, ceiling)
        .snappedTo(detent, ceiling)

    val safeValue = value.coerceIn(0f, ceiling)
    val displayValue = (if (height > 0f) dragY?.let { at(it) } ?: safeValue else safeValue) / ceiling

    Box(
        modifier = modifier
            .onSizeChanged { height = it.height.toFloat() }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragY = offset.y
                        currentOnValueChange(at(offset.y))
                    },
                    onDragEnd = { dragY = null },
                    onDragCancel = { dragY = null }
                ) { change, dragAmount ->
                    change.consume()
                    dragY = (dragY ?: 0f) + dragAmount.y
                    currentOnValueChange(at(dragY!!))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset -> currentOnValueChange(at(offset.y)) }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Track
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(inactiveColor)
        )
        // Active Track
        fill(displayValue)
    }
}

/** How much of the track counts as "on the detent" — 4 %, about 3 px of an 80 dp bar. */
private const val DETENT_BAND = 0.04f

/**
 * [this] pulled onto [detent] when it lands within [DETENT_BAND] of the track's height.
 *
 * Measured against the **track**, not against the value: with a 300 % ceiling a band
 * expressed in level units would be three times wider than it looks.
 */
private fun Float.snappedTo(detent: Float?, ceiling: Float): Float {
    if (detent == null || ceiling <= 0f) return this
    val distance = (this - detent) / ceiling
    return if (distance > -DETENT_BAND && distance < DETENT_BAND) detent else this
}
