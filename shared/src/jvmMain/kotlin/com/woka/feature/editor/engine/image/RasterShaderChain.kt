package com.woka.feature.editor.engine.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.unit.IntSize
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
 * The two pieces a shader chain is built out of — the image at the bottom of it, and the
 * bytes an effect's uniforms become.
 *
 * Split from `RasterImageDraw` by ME-205, which needed room there for a stage that runs
 * *before* the colour filter. The seam is a real one: that file decides **which draw** a
 * recipe takes, and this one knows **how a chain's pieces are made**.
 */

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
internal fun imageShaderFor(image: Image, source: ImageBitmap, sourceSize: IntSize): Shader {
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
internal fun pack(stage: RasterStage.Shader): ByteArray {
    val values = stage.uniforms + listOfNotNull(stage.resolutionScale)
    val buffer = ByteBuffer.allocate(values.size * FLOAT_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    values.forEach { buffer.putFloat(it) }
    return buffer.array()
}
