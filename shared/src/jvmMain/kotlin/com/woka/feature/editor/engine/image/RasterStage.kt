package com.woka.feature.editor.engine.image

import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.ToolStage

/**
 * What an image tool performs, in the three shapes a canvas can draw (§3.2).
 *
 * Three and no more, because the fold has to **collapse** them: everything a tool
 * does becomes one matrix, one colour filter and an ordered paint list, and a
 * fourth kind would be a fourth thing the canvas has to do per frame. A brightness
 * slider at 60 fps therefore costs one filter rebuild and zero pixel work — the
 * live canvas allocates no bitmap at all while editing.
 */
sealed interface RasterStage : ToolStage {

    /** Crop, rotate, flip, resize. [outputSize] is the size *after* this stage. */
    data class Geometry(val matrix: Matrix, val outputSize: IntSize) : RasterStage

    /**
     * Adjustments and filter presets.
     *
     * Carries a `ColorMatrix`, **not** a `ColorFilter`, so the fold can multiply
     * two of them into one. A `ColorFilter` is opaque — chaining them allocates
     * per frame and cannot be flattened, which is the thing ME-007 step 4 exists
     * to prevent. The single `ColorFilter` is built once, at the end of the fold.
     */
    data class Pixels(val colorMatrix: ColorMatrix) : RasterStage

    /** Markup. Drawn in source coordinates, after the matrix and the filter. */
    data class Paint(val draw: DrawScope.() -> Unit) : RasterStage
}
