package com.woka.feature.editor.engine.video.render

import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.engine.image.RasterRecipe
import com.woka.feature.editor.save.renderAtNativeScale
import com.woka.feature.viewer.video.engine.decode.toComposeBitmap
import org.bytedeco.javacv.Frame
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer

/**
 * A decoded frame drawn through the recipe at scale 1.0 — **the still save's own
 * `renderAtNativeScale`**, so a rendered film matches the preview the way a saved photograph
 * does (`render.md` §3.3). A shader chain written a second time is how an export starts
 * disagreeing with what was approved.
 *
 * **One output `Frame` for the whole render, filled in place.** The recorder converts it
 * synchronously inside `record`, so nothing reads it after the next frame overwrites it —
 * and nothing frees it under a reader either, which is the trap ME-181's converter set.
 * It is a plain RGBA frame with an explicit stride, recorded with `AV_PIX_FMT_RGBA` named,
 * so nothing about its layout is left for the recorder to infer.
 */
internal class RecipePicture(
    private val sourceSize: IntSize,
    private val recipe: RasterRecipe
) : FramePicture {

    override val outputSize = evenSize(recipe.outputSize.width, recipe.outputSize.height)

    private val stride = outputSize.width * 4
    private val out = Frame(outputSize.width, outputSize.height, Frame.DEPTH_UBYTE, 4, stride)
    private val pixels = Bitmap().apply {
        allocPixels(ImageInfo(outputSize.width, outputSize.height, ColorType.RGBA_8888, ColorAlphaType.PREMUL))
    }

    override fun draw(frame: Frame): Frame {
        val drawn = renderAtNativeScale(frame.toComposeBitmap(), sourceSize, recipe)
        try {
            // The top-left even-sized region: an odd crop loses its last row or column.
            drawn.readPixels(pixels, 0, 0)
        } finally {
            drawn.close()
        }
        val bytes = pixels.readPixels() ?: return out
        val buffer = out.image[0] as ByteBuffer
        buffer.rewind()
        buffer.put(bytes, 0, minOf(bytes.size, buffer.capacity()))
        buffer.rewind()
        return out
    }
}

/**
 * Whether [recipe] leaves a frame exactly as decoded — no geometry, no grade, no shader,
 * no markup. A document holding only cuts folds to this, and that is what lets a render
 * copy packets instead of encoding (`render.md` §3.2).
 */
internal fun RasterRecipe.isIdentity(sourceSize: IntSize): Boolean =
    matrix.values.contentEquals(IDENTITY) &&
        outputSize == sourceSize &&
        colorFilter == null &&
        shaders.isEmpty() &&
        paints.isEmpty()

private val IDENTITY = Matrix().values

/** H.264 in 4:2:0 wants both sides even; a crop to an odd width loses its last column. */
internal data class EvenSize(val width: Int, val height: Int)

internal fun evenSize(width: Int, height: Int) =
    EvenSize((width - width % 2).coerceAtLeast(2), (height - height % 2).coerceAtLeast(2))

/** What a frame looks like after the recipe, at the encoder's size. */
internal interface FramePicture {
    val outputSize: EvenSize
    fun draw(frame: Frame): Frame
}
