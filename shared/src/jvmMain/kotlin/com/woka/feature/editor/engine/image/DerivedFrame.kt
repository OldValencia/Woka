package com.woka.feature.editor.engine.image

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What a geometry stage does to the frame, when the stage did not say.
 *
 * A rotation knows how to turn the picture and nothing about how large it is —
 * `stage()` receives only parameters (§3.4). So it declares the turn and this works
 * out the rest: the four corners of [frame] are mapped through [matrix], the bounding
 * box of where they land is the new frame, and the matrix is **normalised** by a
 * leading translation so that box starts at the origin.
 *
 * Without the normalisation a 90° turn about the origin puts the whole picture in
 * negative x, off the canvas — which is exactly the compensating translation each
 * geometry tool would otherwise have had to compute for itself, using a size it does
 * not have.
 *
 * Returns the normalised matrix and the frame it produces. A crop never comes here: it
 * chooses its frame and reports it, and that difference is the whole meaning of
 * `Geometry.outputSize` being nullable.
 */
internal fun derivedFrame(frame: IntSize, matrix: Matrix): Pair<Matrix, IntSize> {
    val width = frame.width.toFloat()
    val height = frame.height.toFloat()
    val corners = listOf(
        matrix.map(Offset(0f, 0f)),
        matrix.map(Offset(width, 0f)),
        matrix.map(Offset(width, height)),
        matrix.map(Offset(0f, height))
    )

    val left = corners.minOf { it.x }
    val top = corners.minOf { it.y }
    val right = corners.maxOf { it.x }
    val bottom = corners.maxOf { it.y }

    // The stage first, *then* the shift back into view — `concat(into, next)` is
    // "apply into, then apply next", and the other way round translates before
    // rotating, which puts the picture somewhere else entirely.
    val normalised = Matrix(matrix.values.copyOf())
    concat(normalised, Matrix().apply { translate(-left, -top) })

    // At least one pixel: a degenerate matrix must not produce a zero-sized surface
    // that the save path then hands to Skia.
    return normalised to IntSize(
        width = max(1, (right - left).roundToInt()),
        height = max(1, (bottom - top).roundToInt())
    )
}
