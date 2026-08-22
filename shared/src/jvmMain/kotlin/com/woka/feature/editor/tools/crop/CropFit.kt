package com.woka.feature.editor.tools.crop

import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Fitting a crop to a ratio (ME-109), split from `CropMath` under the size mandate.
 *
 * Three questions, all of them "what shape should this rectangle be": what a freshly
 * chosen ratio looks like, what an existing rect becomes when a ratio is chosen, and
 * what a drag with a ratio locked settles on.
 */

/**
 * The largest rect of [ratio] that fits in [frame], centred — what choosing an aspect
 * does before anything has been dragged.
 *
 * A null ratio is [CropAspect.Free], which is the whole frame.
 */
fun cropFitting(frame: IntSize, ratio: Float?): CropRect {
    if (frame.width <= 0 || frame.height <= 0) return CropRect.Unset
    if (ratio == null || ratio <= 0f || !ratio.isFinite()) return CropRect.of(frame)

    val byWidth = frame.width / ratio
    val size = if (byWidth <= frame.height) {
        IntSize(frame.width, byWidth.toInt())
    } else {
        IntSize((frame.height * ratio).toInt(), frame.height)
    }
    return CropRect(
        left = (frame.width - size.width) / 2,
        top = (frame.height - size.height) / 2,
        width = max(1, size.width),
        height = max(1, size.height)
    ).clampedTo(frame)
}

/**
 * The same rect at [ratio], anchored on the edges [handle] did **not** drag.
 *
 * A corner anchors the corner opposite it. An edge anchors the edge opposite it and
 * grows the other axis about the centre, which is the only way a single-axis drag can
 * honour a ratio without the rect appearing to slide sideways.
 *
 * The leading axis is the one the handle drags — width for a side handle, and width by
 * convention for a corner, so a diagonal drag does not flip between axes mid-gesture.
 */
internal fun CropRect.reshapedTo(ratio: Float, handle: CropHandle, frame: IntSize): CropRect {
    val floor = minSideIn(frame)
    var w = width
    var h = height

    if (handle.movesTop || handle.movesBottom) {
        if (!handle.movesLeft && !handle.movesRight) w = (h * ratio).toInt()
    }
    if (handle.movesLeft || handle.movesRight) h = (w / ratio).toInt()

    // Fit inside the frame, keeping the ratio: shrink whichever axis overflows and let
    // the other follow, rather than clamping one and silently breaking the shape.
    if (w > frame.width) {
        w = frame.width
        h = (w / ratio).toInt()
    }
    if (h > frame.height) {
        h = frame.height
        w = (h * ratio).toInt()
    }
    w = w.coerceIn(floor, frame.width)
    h = h.coerceIn(floor, frame.height)

    val l = if (handle.movesLeft) right - w else if (handle.movesRight) left else centred(left, width, w)
    val t = if (handle.movesTop) bottom - h else if (handle.movesBottom) top else centred(top, height, h)
    return CropRect(l, t, w, h).clampedTo(frame)
}

/** The origin that keeps a side of [was] centred when it becomes [now]. */
private fun centred(origin: Int, was: Int, now: Int): Int = origin + (was - now) / 2

/**
 * This rect at [ratio], keeping its **area** and its centre — what choosing a ratio
 * does to a crop that already exists.
 *
 * **Area, not "the shorter side"** (2026-08-22). Taking the shorter side loses a little
 * every time, so flipping between `1:1` and `Original` walked the rectangle down to the
 * minimum a few presses later. Holding the area makes the round trip return the size it
 * started at: 200×200 at 4:3 is 231×173, and back at 1:1 it is 200×200 again.
 *
 * The frame still wins: a shape that will not fit is scaled down whole, ratio intact,
 * rather than clamped on one axis into something that is no longer the ratio asked for.
 */
fun CropRect.fittedTo(ratio: Float, frame: IntSize): CropRect {
    if (frame.width <= 0 || frame.height <= 0) return this
    if (ratio <= 0f || !ratio.isFinite()) return this

    val floor = minSideIn(frame)
    val area = width.toDouble() * height
    var w = sqrt(area * ratio).roundToInt()
    var h = (w / ratio).roundToInt()

    if (w > frame.width) {
        w = frame.width
        h = (w / ratio).roundToInt()
    }
    if (h > frame.height) {
        h = frame.height
        w = (h * ratio).roundToInt()
    }
    w = max(floor, w).coerceAtMost(frame.width)
    h = max(floor, h).coerceAtMost(frame.height)

    return CropRect(
        left = centred(left, width, w),
        top = centred(top, height, h),
        width = w,
        height = h
    ).clampedTo(frame)
}
