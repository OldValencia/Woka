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
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One float is four bytes, and Skia reads uniforms packed exactly that tightly. */
private const val FLOAT_BYTES = 4

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
    if (recipe.shaders.isEmpty()) {
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

/**
 * The picture sampled in **source** coordinates, whatever size the bitmap actually is.
 *
 * That is the whole reason a shader can be written once and run against the ≤2560 px
 * proxy, the native bitmap at save, and a 400 px grid tile: it evaluates its child at
 * source coordinates in all three, exactly as `dstSize = sourceSize` lets the plain
 * `drawImage` path reason in source pixels and never learn the bitmap was smaller.
 *
 * **The scale is `sourceSize / image`, not the other way round.** A shader's local
 * matrix is applied to the coordinate on the way *in*: Skia samples the texel at
 * `L⁻¹ · q`, so mapping a source coordinate onto a smaller bitmap needs the larger
 * number on top. Inverting it draws a corner of the picture blown up across the frame,
 * which `RasterShaderTest` asserts against by halves of two colours.
 *
 * `LINEAR` rather than Skia's nearest-neighbour default, because on the proxy a source
 * coordinate falls between texels by construction.
 */
private fun imageShaderFor(image: Image, source: ImageBitmap, sourceSize: IntSize): Shader {
    val sx = if (source.width > 0) sourceSize.width.toFloat() / source.width else 1f
    val sy = if (source.height > 0) sourceSize.height.toFloat() / source.height else 1f
    return image.makeShader(
        FilterTileMode.CLAMP,
        FilterTileMode.CLAMP,
        SamplingMode.LINEAR,
        Matrix33.makeScale(sx, sy)
    )
}

/**
 * Declaration order, little-endian, `resolutionScale` last **when the effect declares
 * one at all** — the contract `RasterStage.Shader` states and the packing
 * `HdrImagePainter` has always used. An effect that reads one pixel appends nothing,
 * because a uniform it never reads is one SkSL may drop from the layout.
 */
private fun pack(stage: RasterStage.Shader): ByteArray {
    val values = stage.uniforms + listOfNotNull(stage.resolutionScale)
    val buffer = ByteBuffer.allocate(values.size * FLOAT_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    values.forEach { buffer.putFloat(it) }
    return buffer.array()
}
