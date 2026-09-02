package com.woka.feature.editor.save

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.woka.feature.editor.engine.image.RasterRecipe
import com.woka.feature.editor.engine.image.drawRecipeImage
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface

/**
 * The recipe again, at scale 1.0, against the native bitmap.
 *
 * **The same draw routine as the screen.** `CanvasDrawScope` gives a real Compose
 * `DrawScope` over a Skia surface, so `RasterStage.Paint`'s `DrawScope.() -> Unit`
 * runs here byte for byte as it runs in `RasterSurface` — which is what makes "markup
 * rasterised at source resolution" a property of the design rather than a second
 * implementation somebody has to keep in step.
 *
 * There is exactly one difference from the screen path, and it is the whole point:
 * no fit scale. The surface is `recipe.outputSize`, the matrix maps source to output,
 * and the source bitmap is drawn at its own size. What comes out is the file.
 *
 * Density is 1.0 deliberately. Every value in a recipe is in **pixels** — a crop rect,
 * a stroke width, a font size in markup — and letting the display's density in here
 * would make a saved file differ between two monitors.
 */
internal fun renderAtNativeScale(
    source: ImageBitmap,
    sourceSize: IntSize,
    recipe: RasterRecipe
): Image = Surface.makeRasterN32Premul(
    recipe.outputSize.width,
    recipe.outputSize.height
).use { surface ->
    val canvas = surface.canvas.asComposeCanvas()
    val size = Size(recipe.outputSize.width.toFloat(), recipe.outputSize.height.toFloat())

    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, size) {
        withTransform({ transform(recipe.matrix) }) {
            // The same function the screen calls, not a copy of it (ME-165). A shader
            // chain written twice is how a saved file starts disagreeing with the
            // preview the user approved.
            drawRecipeImage(source, sourceSize, recipe)
        }
        // Outside the transform, exactly as on screen: a crop clips markup rather
        // than moving it (§3.4's application order).
        recipe.paints.forEach { paint -> paint.draw(this) }
    }
    surface.makeImageSnapshot()
}
