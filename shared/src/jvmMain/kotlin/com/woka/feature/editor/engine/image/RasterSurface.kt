package com.woka.feature.editor.engine.image

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min

/**
 * The picture, through the recipe, in one draw pass.
 *
 * **Three coordinate spaces meet here, and the order is the whole ticket.**
 *
 * - `recipe.matrix` is expressed in **source** pixels — that is what a tool's stage
 *   returns and what a crop rect is measured in;
 * - [source] is source × *sourceScale*: the ≤2560 px proxy at Fit, the native bitmap
 *   while the canvas is zoomed past what the proxy can answer (ME-116), and the native
 *   one again on save — this function cannot tell, and must not need to;
 * - this composable is handed a box measuring `recipe.outputSize` × *fitScale*.
 *
 * So: scale into output space, apply the matrix there — it maps source to output —
 * and stretch the bitmap back up to source size as it is drawn. Every scale is named
 * once, in this function. A second place computing any of them is the bug this comment
 * exists to prevent, and its symptom is a picture that looks perfect **until the first
 * crop**, because until then every matrix is the identity and the errors cancel.
 *
 * **No bitmap is allocated per frame**, which is §3.2's claim: the colour filter goes
 * to the `drawImage` call rather than wrapping a layer, and the geometry is a
 * transform rather than a re-render.
 */
@Composable
internal fun RasterSurface(
    source: ImageBitmap,
    sourceSize: IntSize,
    recipe: RasterRecipe,
    modifier: Modifier = Modifier,
    /**
     * Whether the picture **fills** the box instead of fitting inside it.
     *
     * False for the canvas, whose box is measured to `drawnSize`'s own proportions — the
     * two scales are equal there and this parameter changes nothing. True for a
     * preview-choice thumbnail, whose box is a **square** whatever shape the picture is:
     * a 16:9 frame fitted into one drew across the top and left the bottom empty
     * (reported 2026-09-03). A look strip is for judging colour rather than composition,
     * so filling and cropping is what the mockup draws and what iOS Photos does.
     */
    fill: Boolean = false
) {
    // **Clipped, and a crop is why** (2026-08-22). The box is `recipe.outputSize`; the
    // picture drawn inside it is the *source*, which a crop makes larger than the box.
    // A Compose `Canvas` does not clip, so the discarded part kept being drawn outside
    // the frame — the editor showed a wider picture than the crop it had applied, and
    // zooming out revealed the whole of it. Every operation before the crop produced an
    // output at least as large as what it drew, so nothing had ever exceeded the box.
    Canvas(modifier = modifier.fillMaxSize().clipToBounds()) {
        val output = recipe.outputSize
        // Before the first layout, and for a document whose fold produced nothing to
        // draw. Returning is invisible for a frame; dividing by zero is not.
        if (output.width <= 0 || output.height <= 0) return@Canvas
        if (sourceSize.width <= 0 || sourceSize.height <= 0) return@Canvas

        // **Both axes, and centred** (2026-09-03). It was `size.width / output.width`
        // anchored at the origin, which is right only while the box has the picture's own
        // proportions — true of the canvas and false of every thumbnail.
        val across = size.width / output.width.toFloat()
        val down = size.height / output.height.toFloat()
        val fitScale = if (fill) max(across, down) else min(across, down)
        // Zero on the canvas, where the two scales are equal. What a `fill` crops is
        // split between the two edges rather than taken off one of them.
        val left = (size.width - output.width * fitScale) / 2f
        val top = (size.height - output.height * fitScale) / 2f

        withTransform({
            translate(left, top)
            scale(fitScale, fitScale, pivot = Offset.Zero)
        }) {
            withTransform({ transform(recipe.matrix) }) {
                // Everything inside this call reasons in source pixels and never
                // learns the bitmap was smaller. That is what lets the same recipe run
                // here against a 2560 px copy and again on save against the native
                // bitmap, with no branch between them — and it is what ME-116 spends:
                // swapping the two at zoom needs no logic here at all. Shared with the
                // save path rather than written twice, so the two cannot drift.
                drawRecipeImage(source, sourceSize, recipe)
            }
            // Markup is drawn in source coordinates too, but *outside* the matrix:
            // a crop must clip an arrow, not move it (§3.4's application order). After
            // the shaders as well as the filter: markup sits on top of a sharpened
            // picture rather than being sharpened with it (ME-165).
            recipe.paints.forEach { paint -> paint.draw(this) }
        }
    }
}
