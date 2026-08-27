package com.woka.feature.viewer.shell

import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/** Resistance applied to movement past the edge, before it springs back. */
private const val RUBBER_BAND_FACTOR = 0.35f

/**
 * How far the media may be moved from centre on each axis.
 *
 * **The larger of two rules, per axis** (asked for 2026-08-28). The first is the
 * old one: enough to bring the media's edge to the panel's edge, so a magnified
 * picture is explored without ever showing background beside it. The second is a
 * floor of half the panel, which is what makes the picture movable *at all* when it
 * is not larger than the panel — the case that used to be pinned to centre.
 *
 * Half the panel is where it stops because that is the last position at which the
 * media's own centre is still on screen: past it there is no longer an obvious way
 * back, and a picture dragged out of the window and lost is the failure a free pan
 * has to avoid. Above roughly 200 % the first rule is the larger of the two and
 * nothing about the magnified case changes.
 *
 * **It no longer recentres the frame when the zoom comes back down**, which the
 * zero used to do for free. That is the price of the ask: a picture moved off
 * centre stays where it was put until it is moved back.
 */
internal fun panLimits(sizeMode: SizeMode, container: IntSize, native: IntSize): Pair<Float, Float> {
    if (container == IntSize.Zero || native.width <= 0 || native.height <= 0) return 0f to 0f
    val cw = container.width.toFloat()
    val ch = container.height.toFloat()
    val fitScale = min(cw / native.width, ch / native.height)
    val graphicsScale = computeGraphicsScale(sizeMode, container, native)
    val shownWidth = native.width * fitScale * graphicsScale
    val shownHeight = native.height * fitScale * graphicsScale
    return max(cw / 2f, (shownWidth - cw) / 2f) to max(ch / 2f, (shownHeight - ch) / 2f)
}

/** Hard clamp — used when a drag ends and when the zoom changes. */
internal fun clampOffset(
    offsetX: Float,
    offsetY: Float,
    sizeMode: SizeMode,
    container: IntSize,
    native: IntSize
): Pair<Float, Float> {
    val (limitX, limitY) = panLimits(sizeMode, container, native)
    return offsetX.coerceIn(-limitX, limitX) to offsetY.coerceIn(-limitY, limitY)
}

/** Soft clamp — used while dragging, so the edge resists instead of stopping dead. */
internal fun rubberBandOffset(
    offsetX: Float,
    offsetY: Float,
    sizeMode: SizeMode,
    container: IntSize,
    native: IntSize
): Pair<Float, Float> {
    val (limitX, limitY) = panLimits(sizeMode, container, native)
    return band(offsetX, limitX) to band(offsetY, limitY)
}

private fun band(value: Float, limit: Float): Float =
    if (abs(value) <= limit) value
    else sign(value) * (limit + (abs(value) - limit) * RUBBER_BAND_FACTOR)
