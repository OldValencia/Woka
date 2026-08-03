package com.woka.ui.viewer

import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/** Resistance applied to movement past the edge, before it springs back. */
private const val RUBBER_BAND_FACTOR = 0.35f

/**
 * How far the media may be moved from centre on each axis before its edge would
 * cross the panel edge. Zero on an axis where the media is not larger than the
 * panel — there the axis is pinned to centre, which is what auto-recentres the
 * frame when the zoom comes back down.
 */
internal fun panLimits(sizeMode: SizeMode, container: IntSize, native: IntSize): Pair<Float, Float> {
    if (container == IntSize.Zero || native.width <= 0 || native.height <= 0) return 0f to 0f
    val cw = container.width.toFloat()
    val ch = container.height.toFloat()
    val fitScale = min(cw / native.width, ch / native.height)
    val graphicsScale = computeGraphicsScale(sizeMode, container, native)
    val shownWidth = native.width * fitScale * graphicsScale
    val shownHeight = native.height * fitScale * graphicsScale
    return max(0f, (shownWidth - cw) / 2f) to max(0f, (shownHeight - ch) / 2f)
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
