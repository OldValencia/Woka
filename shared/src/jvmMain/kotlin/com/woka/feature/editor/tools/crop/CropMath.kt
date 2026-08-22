package com.woka.feature.editor.tools.crop

import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min

/**
 * The smallest side a crop may be dragged to, in source pixels.
 *
 * Small enough not to be in the way, large enough that a rect cannot be reduced to
 * something the handles overlap and nobody can grab again.
 */
internal const val MIN_CROP_PX = 32

/**
 * Everything a crop rect does, as functions of plain values (ME-109).
 *
 * **Compose-free and pure by design.** The overlay is gestures and drawing; every
 * decision about where a rect ends up is here, where it is asserted without a window.
 * The rule the file exists to hold: a rect never leaves the frame and never goes below
 * [MIN_CROP_PX], whatever order of drags produced it.
 *
 * All coordinates are **source** pixels, which is the only space a stored crop is ever
 * expressed in (`CropRect`).
 */

/** Inside [frame], at least a pixel, and never larger than the frame itself. */
fun CropRect.clampedTo(frame: IntSize): CropRect {
    if (frame.width <= 0 || frame.height <= 0) return this
    val w = width.coerceIn(1, frame.width)
    val h = height.coerceIn(1, frame.height)
    return CropRect(
        left = left.coerceIn(0, frame.width - w),
        top = top.coerceIn(0, frame.height - h),
        width = w,
        height = h
    )
}

/** Dragged bodily. The size is kept; the frame stops it. */
fun CropRect.movedBy(dx: Int, dy: Int, frame: IntSize): CropRect =
    CropRect(left + dx, top + dy, width, height).clampedTo(frame)

/**
 * One handle dragged by [dx], [dy].
 *
 * The edges a handle owns move; the others stay exactly where they are, which is what
 * makes dragging the right edge keep the left one fixed without a case per handle
 * (`CropHandle.movesLeft` and friends).
 *
 * With [ratio] set, the free result is then reshaped around the anchor the handle
 * implies — see [reshapedTo]. Without one it is only clamped.
 */
fun CropRect.resizedBy(
    handle: CropHandle,
    dx: Int,
    dy: Int,
    frame: IntSize,
    ratio: Float? = null
): CropRect {
    if (handle == CropHandle.Body) return movedBy(dx, dy, frame)
    if (frame.width <= 0 || frame.height <= 0) return this

    val floor = minSideIn(frame)
    var l = left
    var t = top
    var r = right
    var b = bottom

    if (handle.movesLeft) l = (l + dx).coerceIn(0, max(0, r - floor))
    if (handle.movesRight) r = (r + dx).coerceIn(min(l + floor, frame.width), frame.width)
    if (handle.movesTop) t = (t + dy).coerceIn(0, max(0, b - floor))
    if (handle.movesBottom) b = (b + dy).coerceIn(min(t + floor, frame.height), frame.height)

    val free = CropRect(l, t, r - l, b - t).clampedTo(frame)
    return if (ratio == null) free else free.reshapedTo(ratio, handle, frame)
}

/** [MIN_CROP_PX], unless the picture itself is smaller than that. */
internal fun minSideIn(frame: IntSize): Int =
    max(1, min(MIN_CROP_PX, min(frame.width, frame.height)))
