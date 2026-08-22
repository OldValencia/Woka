package com.woka.feature.editor.engine.image

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntSize

/**
 * The picture, through the recipe, in one draw pass.
 *
 * **Three coordinate spaces meet here, and the order is the whole ticket.**
 *
 * - `recipe.matrix` is expressed in **source** pixels — that is what a tool's stage
 *   returns and what a crop rect is measured in;
 * - the proxy bitmap is source × *proxyScale*, because it was capped at 2560 px;
 * - this composable is handed a box measuring `recipe.outputSize` × *fitScale*.
 *
 * So: scale into output space, apply the matrix there — it maps source to output —
 * and stretch the proxy back up to source size as it is drawn. Every scale is named
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
    proxy: ImageBitmap,
    sourceSize: IntSize,
    recipe: RasterRecipe,
    modifier: Modifier = Modifier
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

        val fitScale = size.width / output.width.toFloat()

        scale(scale = fitScale, pivot = Offset.Zero) {
            withTransform({ transform(recipe.matrix) }) {
                // `dstSize` is the *source's* dimensions, so everything above this
                // call reasons in source pixels and never learns the proxy was
                // smaller. That is what lets the same recipe run here against a
                // 2560 px copy and again on save against the native bitmap, with no
                // branch between them.
                drawImage(
                    image = proxy,
                    srcSize = IntSize(proxy.width, proxy.height),
                    dstSize = sourceSize,
                    colorFilter = recipe.colorFilter
                )
            }
            // Markup is drawn in source coordinates too, but *outside* the matrix:
            // a crop must clip an arrow, not move it (§3.4's application order).
            recipe.paints.forEach { paint -> paint.draw(this) }
        }
    }
}
