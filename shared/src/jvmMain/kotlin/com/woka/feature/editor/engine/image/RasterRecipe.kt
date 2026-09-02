package com.woka.feature.editor.engine.image

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.ToolOp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A whole document collapsed into what one draw call needs (§3.2).
 *
 * The canvas draws: source → [matrix] → [colorFilter] → [shaders] → [paints]. The same recipe
 * runs at screen scale for the preview and at scale 1.0 against the native bitmap
 * on save, which is what makes "markup rasterised at source resolution" fall out
 * of the design instead of being a special case.
 *
 * [colorFilter] is **null** when no colour operation is present, so a document
 * that only crops costs nothing at draw time — an identity filter would still be
 * a filter the GPU applies to every pixel.
 *
 * [shaders] are ordered and run in sequence rather than collapsed, because two
 * convolutions do not compose the way two colour matrices do (ME-165). Empty for
 * every document that has no such adjustment in it, which is the common case and
 * costs exactly the draw call it always did.
 *
 * [skipped] carries operations whose tool is missing or switched off. They are
 * reported in the inspector rather than dropped silently or thrown on: §3.4's
 * promise is that a document never crashes and never silently loses work, and a
 * disabled tool must be re-enableable with its operations intact.
 */
@Immutable
data class RasterRecipe(
    val matrix: Matrix,
    val outputSize: IntSize,
    val colorFilter: ColorFilter?,
    val shaders: List<RasterStage.Shader> = emptyList(),
    val paints: List<RasterStage.Paint>,
    val skipped: List<ToolOp>
)

/**
 * The same recipe, expressed in the coordinates of a bitmap [factor] times the source
 * (ME-109).
 *
 * **This is what a crop needs and a rotation never did.** A recipe is folded in source
 * pixels — that is what a tool's `stage()` returns and what a crop rect is measured in
 * — while a reader may have decoded the picture small. Rotate and flip are scale-free,
 * so folding them against whatever bitmap arrived happened to be right; a crop rect of
 * `(1200, 800, 600×400)` applied to a 400 px grid cover lands somewhere else entirely.
 *
 * The matrix becomes **S · M · S⁻¹**: scale down into source coordinates, do the work
 * there, scale back. Written as "apply S⁻¹, then M, then S", which is the order
 * [concat] takes and the order this reads in.
 *
 * [RasterRecipe.paints] are carried through untouched, and that is **ME-113's** to fix:
 * nothing produces a paint yet, and a stroke will need the same treatment its own
 * ticket can give it once there is one to scale.
 *
 * [RasterRecipe.shaders] are **not** carried through untouched: a radius stated in
 * source pixels describes a different picture once the bitmap is [factor] the size, so
 * each stage's `resolutionScale` is multiplied by the same number the matrix is. A
 * sharpen folded for a 400 px grid tile is otherwise applied as though the tile were
 * the whole photograph, and reads four times too hard (ME-165).
 */
internal fun RasterRecipe.scaledBy(factor: Float): RasterRecipe {
    // Not merely an optimisation: a reader handed a native bitmap must come back
    // bit-for-bit what it is today, and the identity path is how that is guaranteed.
    if (factor == 1f || factor <= 0f || !factor.isFinite()) return this

    val scaled = Matrix().apply { scale(1f / factor, 1f / factor) }
    concat(scaled, matrix)
    concat(scaled, Matrix().apply { scale(factor, factor) })

    return copy(
        matrix = scaled,
        shaders = shaders.map { it.copy(resolutionScale = it.resolutionScale * factor) },
        // At least one pixel: a recipe scaled into a thumbnail must not describe a
        // zero-sized frame that the renderer then hands to Skia.
        outputSize = IntSize(
            width = max(1, (outputSize.width * factor).roundToInt()),
            height = max(1, (outputSize.height * factor).roundToInt())
        )
    )
}
