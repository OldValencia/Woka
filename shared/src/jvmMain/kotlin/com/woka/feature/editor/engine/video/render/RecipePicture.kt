package com.woka.feature.editor.engine.video.render

import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.StillColorInfo
import com.woka.core.data.TransferKind
import com.woka.feature.editor.engine.image.RasterRecipe
import com.woka.feature.editor.engine.video.hdrStageFor
import com.woka.feature.editor.save.renderAtNativeScale
import com.woka.feature.viewer.video.engine.decode.toComposeBitmap
import com.woka.feature.viewer.video.engine.probe.scenePeak
import org.bytedeco.ffmpeg.avutil.AVFrame
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
 *
 * **One for the whole render, not one per clip** ([RenderBuffers], handed in by [buffersOf]):
 * every clip held its own pair of full-size buffers, made up front — ~1.3 GB for twenty 4K
 * clips before the first frame (1.0.6 review). Clips play one at a time and share the film's
 * geometry, so one pair per output size is all a render ever draws into.
 *
 * **The HDR stage is built here, per scene** (ME-305): [colour] carries the film's white
 * point, and a frame carrying an HDR10+ scene peak swaps it in — rebuilt only when the peak
 * changes, which is at a cut. The encoder's grabber is a `FreshFrameGrabber`, so the side
 * data on `frame.opaque` is that frame's own (ME-237).
 */
internal class RecipePicture(
    private val sourceSize: IntSize,
    private val recipe: RasterRecipe,
    /** The film's colour under the HDR mode, or null for a picture that needs none. */
    private val colour: StillColorInfo?,
    private val buffersOf: (EvenSize) -> RenderBuffers
) : FramePicture {

    private var staged = recipe.copy(source = hdrStageFor(colour))
    private var stagedPeak: Float? = null

    override val outputSize = evenSize(recipe.outputSize.width, recipe.outputSize.height)

    override fun draw(frame: Frame): Frame {
        val (out, pixels) = buffersOf(outputSize)
        val drawn = renderAtNativeScale(frame.toComposeBitmap(), sourceSize, stagedFor(frame))
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

    /** The recipe with this frame's scene peak in its HDR stage, rebuilt only at a cut. */
    private fun stagedFor(frame: Frame): RasterRecipe {
        val scene = colour ?: return staged
        if (scene.transfer != TransferKind.PQ) return staged
        val peak = (frame.opaque as? AVFrame)?.scenePeak()
        if (peak == stagedPeak) return staged
        stagedPeak = peak
        staged = recipe.copy(source = hdrStageFor(scene.copy(whitePoint = peak ?: scene.whitePoint)))
        return staged
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

/** The output `Frame` and the Skia readback bitmap a render draws every frame into. */
internal class RenderBuffers(size: EvenSize) : AutoCloseable {
    val out = Frame(size.width, size.height, Frame.DEPTH_UBYTE, 4, size.width * 4)
    val pixels = Bitmap().apply {
        allocPixels(ImageInfo(size.width, size.height, ColorType.RGBA_8888, ColorAlphaType.PREMUL))
    }

    operator fun component1() = out
    operator fun component2() = pixels

    override fun close() {
        pixels.close()
        out.close()
    }
}
