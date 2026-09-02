package com.woka.feature.editor.engine.image

import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.ToolStage
import org.jetbrains.skia.RuntimeEffect

/**
 * What an image tool performs, in the four shapes a canvas can draw (§3.2).
 *
 * **Three of them collapse; the fourth chains.** Everything a tool does becomes one
 * matrix, one colour filter, an ordered shader chain and an ordered paint list. A
 * brightness slider at 60 fps therefore costs one filter rebuild and zero pixel work —
 * the live canvas allocates no bitmap at all while editing, and [Shader] does not
 * change that (see its own note).
 *
 * It was three until 2026-09-02, and the argument for three was sound while every
 * adjustment was affine. ME-165 added the fourth; §9 carries the reversal.
 */
sealed interface RasterStage : ToolStage {

    /**
     * Crop, rotate, flip, resize.
     *
     * [outputSize] is the frame *after* this stage, and it is **nullable on purpose**.
     * A crop *chooses* a frame and says so. A rotation cannot: `stage()` receives only
     * the parameters, so a quarter turn has no way to know it should report the source's
     * height as its width — and a *second* rotation would need the size after the first,
     * which is a running value only the fold holds.
     *
     * Null therefore means **derive it**: the fold maps this stage's matrix over the
     * frame as it stands, takes the bounding box, and normalises the matrix so the box
     * starts at the origin. A rotation consequently declares nothing but its rotation —
     * no size and no compensating translation (ME-105).
     */
    data class Geometry(val matrix: Matrix, val outputSize: IntSize? = null) : RasterStage

    /**
     * Adjustments and filter presets.
     *
     * Carries a `ColorMatrix`, **not** a `ColorFilter`, so the fold can multiply
     * two of them into one. A `ColorFilter` is opaque — chaining them allocates
     * per frame and cannot be flattened, which is the thing ME-007 step 4 exists
     * to prevent. The single `ColorFilter` is built once, at the end of the fold.
     */
    data class Pixels(val colorMatrix: ColorMatrix) : RasterStage

    /**
     * Adjustments a colour matrix cannot express: a tone curve, a convolution.
     *
     * **Why this cannot be [Pixels].** A `ColorMatrix` is a 4×5 **affine** map of the
     * channels. A curve is per-luminance and a blur reads the pixels *around* the one
     * being written, so neither is such a map at all — no multiplication of matrices
     * ever reaches them.
     *
     * **And why it is a list in the recipe rather than one value.** Two colour matrices
     * multiply into one; two convolutions do not compose, so the fold keeps them in
     * order and the draw runs them in sequence, each taking the previous as its child.
     *
     * **It still allocates no bitmap.** The chain is built over a Skia *image shader*
     * — the picture sampled directly, with the colour filter attached to it — not over
     * an offscreen copy. A convolution costs texture fetches per pixel, which is what a
     * convolution costs anywhere.
     *
     * [uniforms] are packed in **declaration order**, little-endian, which is how Skia
     * reads them and how `HdrImagePainter` has always written them.
     *
     * [resolutionScale] says how many coordinate units make one source pixel, so a
     * radius stated in source pixels stays the same *picture* when a reader folds the
     * recipe against a bitmap that is not source-sized. It is 1.0 in a recipe folded at
     * source scale, and `scaledBy` is the only thing that changes it.
     *
     * **It is null for an effect that does not sample its neighbours**, and that is not
     * tidiness (ME-162). Appended, it becomes one more declared uniform, and a uniform
     * an effect never reads is one SkSL is free to eliminate — leaving the packed data a
     * float longer than the layout Skia computed. A tone curve reads one pixel and wants
     * no scale; only a convolution declares one.
     */
    data class Shader(
        val effect: RuntimeEffect,
        val uniforms: List<Float> = emptyList(),
        val resolutionScale: Float? = null
    ) : RasterStage

    /**
     * More than one of the above, from a single tool (ME-162).
     *
     * `EditorTool.stage()` answers one `ToolStage`, and that signature is shared with
     * the video engine — widening it to a list would make every trim tool carry a
     * collection it has no use for. So a tool with several stages says so *here*, in the
     * raster vocabulary, and the fold flattens it in place: one branch, and no other
     * tool changed.
     *
     * Adjust is the first to need it, holding a colour matrix and up to three shaders.
     * Flattened depth-first, so the order written is the order run.
     */
    data class Composite(val stages: List<RasterStage>) : RasterStage

    /** Markup. Drawn in source coordinates, after the matrix, filter and shaders. */
    data class Paint(val draw: DrawScope.() -> Unit) : RasterStage
}
