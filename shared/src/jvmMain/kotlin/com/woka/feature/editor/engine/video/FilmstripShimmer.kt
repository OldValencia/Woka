package com.woka.feature.editor.engine.video

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/** One sweep of the highlight across a slot — slow enough to read as waiting, not alarm. */
private const val SWEEP_MS = 1_200

private val HIGHLIGHT = Color.White.copy(alpha = 0.10f)

/**
 * A slot still decoding, drawn as **work in progress rather than a hole** (reported
 * 2026-11-04: on an 81-minute film the strip stayed grey long enough to look broken).
 *
 * The filmstrip decodes keyframes on one low-priority thread, on purpose, so the picture
 * never stutters for it; this is the honest face of that choice. The sweep runs only while
 * a slot of the tile is empty — `ClipTile` reports that from its draw — so a loaded strip
 * asks for no frames at all.
 */
@Composable
internal fun rememberShimmer(active: Boolean): State<Float> {
    if (!active) return remember { mutableStateOf(0f) }
    return rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SWEEP_MS, easing = LinearEasing), RepeatMode.Restart)
    )
}

/** The sweep over one empty slot at [left], its band placed by [phase] across the tile. */
internal fun DrawScope.drawShimmer(left: Float, slot: Size, phase: Float) {
    val band = size.width.coerceAtLeast(slot.width) * 0.6f
    val centre = -band + (size.width + 2 * band) * phase
    val brush = Brush.horizontalGradient(
        colors = listOf(Color.Transparent, HIGHLIGHT, Color.Transparent),
        startX = centre - band / 2,
        endX = centre + band / 2
    )
    drawRect(brush, topLeft = Offset(left, 0f), size = slot)
}
