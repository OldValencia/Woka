package com.woka.feature.editor.engine.image

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntSize

/**
 * A bitmap drawn through a recipe, as a `Painter` — so a reader that already draws a
 * painter, the viewer's frame, gets an edited frame with no change to its layout (ME-284).
 *
 * **The intrinsic size is the recipe's output**, a crop's proportions and not the frame's,
 * so `ContentScale.Fit` letterboxes the edited picture rather than the source. The drawing
 * is [drawThroughRecipe], the editor canvas's own; clipped, for `RasterSurface`'s reason —
 * a crop draws the whole source and only the box hides the discarded part.
 *
 * No bitmap is allocated per frame: the colour filter rides the `drawImage`, the geometry
 * is a transform.
 */
internal class RecipePainter(
    private val source: ImageBitmap,
    private val sourceSize: IntSize,
    private val recipe: RasterRecipe
) : Painter() {

    override val intrinsicSize: Size =
        Size(recipe.outputSize.width.toFloat(), recipe.outputSize.height.toFloat())

    override fun DrawScope.onDraw() {
        clipRect { drawThroughRecipe(source, sourceSize, recipe) }
    }
}
