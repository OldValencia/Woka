package com.woka.feature.editor.engine.image

import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * [frame] turned by [degrees] about its centre, cut to the largest picture of the same shape
 * the turned one still fills (RD-205). Positive degrees turn clockwise on screen, as
 * `rotateZ` and the prototype's CSS `rotate()` do.
 *
 * **Real pixels only** — the author's choice, 2026-11-26. A frame `W × H` turned by θ covers
 * a centred `W/k × H/k` with `k = cos|θ| + max(W/H, H/W) · sin|θ|`, and that is the output:
 * 6000 × 4000 at 10° keeps 4818 × 3212. Keeping the original size instead would scale the
 * kept area up by `k` — interpolated pixels the file never had, and a larger bitmap to
 * hold for nothing (rejected with the author).
 *
 * **Floored**, not rounded: a size rounded up by a fraction puts the output's corners a
 * hair outside the turned picture, and the save writes those pixels transparent.
 *
 * The matrix carries no scale, so a stroke width, a crop rect and a sharpen radius after it
 * keep meaning source pixels. A `Geometry` with its size named, so the fold takes it as a
 * crop is taken rather than deriving a bounding box (`derivedFrame`).
 */
internal fun straightened(frame: IntSize, degrees: Float): RasterStage.Geometry {
    if (degrees == 0f || frame.width <= 0 || frame.height <= 0) {
        return RasterStage.Geometry(Matrix(), frame)
    }
    val width = frame.width.toDouble()
    val height = frame.height.toDouble()
    val radians = abs(degrees) * PI / 180.0
    val cover = cos(radians) + max(width / height, height / width) * sin(radians)
    val output = IntSize(
        width = max(1, floor(width / cover).toInt()),
        height = max(1, floor(height / cover).toInt())
    )

    // The centre to the origin, the turn, then the origin to the output's centre —
    // `concat(into, next)` is "apply into, then apply next".
    val matrix = Matrix().apply { translate(-frame.width / 2f, -frame.height / 2f) }
    concat(matrix, Matrix().apply { rotateZ(degrees) })
    concat(matrix, Matrix().apply { translate(output.width / 2f, output.height / 2f) })
    return RasterStage.Geometry(matrix, output)
}
