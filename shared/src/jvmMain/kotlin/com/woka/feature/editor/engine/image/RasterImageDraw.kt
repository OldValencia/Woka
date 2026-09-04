package com.woka.feature.editor.engine.image

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.asSkiaColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.Shader

/**
 * The picture through the recipe's colour work — **the one implementation both draw
 * paths use** (ME-165).
 *
 * `RasterSurface` and `renderAtNativeScale` call this rather than each writing the
 * draw themselves, because the property the editor rests on is that a preview and a
 * save produce the same pixels. Two copies of a shader chain is precisely how that
 * stops being true, and nothing would notice until a saved file disagreed with what
 * the user approved.
 *
 * The geometry, the fit scale and the paints stay with the callers: those *are*
 * different between the two: one has a canvas to fit into and the other has a file
 * to fill.
 */
internal fun DrawScope.drawRecipeImage(
    source: ImageBitmap,
    sourceSize: IntSize,
    recipe: RasterRecipe
) {
    // The path everything shipped before ME-165 takes, unchanged and still the common
    // one: no shader means one `drawImage` with a filter attached, and no native handle
    // is created or closed.
    // **`source` forces the chain too** (ME-205): `drawImage` takes a `ColorFilter` and
    // cannot take a shader, so a recipe carrying one has to go the long way even when it
    // has no effect of its own.
    if (recipe.shaders.isEmpty() && recipe.source == null) {
        drawImage(
            image = source,
            srcSize = IntSize(source.width, source.height),
            dstSize = sourceSize,
            colorFilter = recipe.colorFilter
        )
        return
    }
    drawThroughShaders(source, sourceSize, recipe)
}

/**
 * The chain: image → colour filter → each effect in order, drawn as one rect.
 *
 * **No offscreen surface.** The colour filter is attached to the *image shader* and
 * each effect takes the previous shader as its child, so a convolution samples the
 * picture directly. An intermediate surface per frame would have cost §3.2's one real
 * claim — the live canvas allocates no bitmap while editing.
 *
 * **Every native handle is closed** the moment `drawRect` returns, and that is not
 * tidiness. `HdrImagePainter` learned it the expensive way: a runtime-effect shader
 * holds its child, which holds the image, which shares the bitmap's pixels, so an
 * unclosed chain pins a whole frame. Closing here is safe because the canvas took its
 * own references while recording, and those outlive ours.
 */
private fun DrawScope.drawThroughShaders(
    source: ImageBitmap,
    sourceSize: IntSize,
    recipe: RasterRecipe
) {
    val image = Image.makeFromBitmap(source.asSkiaBitmap())
    val built = mutableListOf<Shader>()
    val data = mutableListOf<Data>()
    try {
        var child = imageShaderFor(image, source, sourceSize)
        built += child
        // **Before the colour filter, and that is the whole reason it is its own field.**
        // The filter is Adjust and Filters collapsed into one matrix, and it was written
        // for ordinary sRGB light; a tone map placed anywhere in `shaders` would run
        // *after* it, which grades a PQ signal and then maps the result (ME-205).
        recipe.source?.let { stage ->
            val uniforms = Data.makeFromBytes(pack(stage))
            data += uniforms
            child = stage.effect.makeShader(uniforms, arrayOf(child), null)
            built += child
        }
        recipe.colorFilter?.let { filter ->
            child = child.makeWithColorFilter(filter.asSkiaColorFilter())
            built += child
        }
        recipe.shaders.forEach { stage ->
            val uniforms = Data.makeFromBytes(pack(stage))
            data += uniforms
            child = stage.effect.makeShader(uniforms, arrayOf(child), null)
            built += child
        }
        drawRect(
            brush = ShaderBrush(child.asComposeShader()),
            size = Size(sourceSize.width.toFloat(), sourceSize.height.toFloat())
        )
    } finally {
        built.asReversed().forEach { it.close() }
        data.forEach { it.close() }
        image.close()
    }
}
