package com.woka.feature.editor.engine.image

import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Matrix

/**
 * Composition for the two things the fold accumulates, kept out of `RasterFold.kt`
 * so that file stays about ordering (ME-007 step 4).
 *
 * Both operators compose **left-applied-first**: given stages in application
 * order, `compose(accumulated, next)` is "do everything so far, then `next`".
 * Matrix and colour-matrix products are not commutative, so this direction is the
 * difference between rotating a cropped picture and cropping a rotated one.
 */

/** `into` becomes "apply [into], then apply [next]". */
internal fun concat(into: Matrix, next: Matrix) {
    // `timesAssign` computes `this = this * other`, and for column vectors the
    // right-hand factor acts first — so the accumulated transform goes on the
    // right and the new stage on the left.
    val composed = Matrix(next.values.copyOf())
    composed *= into
    into.setFrom(composed)
}

/**
 * The single colour matrix for two stages, or [next] when nothing precedes it.
 *
 * Multiplied rather than chained as `ColorFilter`s: a chain allocates one filter
 * per stage per frame and cannot be flattened, whereas the product is computed
 * once here and rebuilt only when a parameter actually changes.
 */
internal fun multiply(accumulated: ColorMatrix?, next: ColorMatrix): ColorMatrix {
    val previous = accumulated ?: return ColorMatrix(next.values.copyOf())
    val composed = ColorMatrix(next.values.copyOf())
    composed *= previous
    return composed
}

/**
 * Whether this matrix would change any pixel.
 *
 * Checked so an all-neutral document yields `colorFilter = null` — an identity
 * filter is still a filter the GPU runs over every pixel of every frame.
 */
internal fun ColorMatrix.isIdentity(): Boolean = values.contentEquals(ColorMatrix().values)
