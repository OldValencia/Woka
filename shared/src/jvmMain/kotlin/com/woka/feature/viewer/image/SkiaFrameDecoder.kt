package com.woka.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.woka.core.data.FrameKind
import com.woka.core.data.ImageFrame
import com.woka.core.data.ImageFrameSet
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import java.io.File

/**
 * Skia's multi-frame path: the frames `Image.makeFromEncoded` throws away.
 *
 * `makeFromEncoded` collapses a frame set to its first frame **silently**, which
 * is the whole reason an animated GIF has always sat still in the viewer. Only
 * `Codec` counts frames, so anything that wants more than one has to come here.
 *
 * Covers `.gif` and `.webp` animation with no new dependency — measured
 * 2026-08-07, both report all their frames and their per-frame durations. It
 * does **not** cover HEIF, AVIF or TIFF: skia has no codec for any of them
 * (`IllegalArgumentException: Unsupported format`), and they are Phase 2b's.
 */
internal object SkiaFrameDecoder {

    /** Frames, kinds and timings — without decoding a single pixel. */
    fun frameSet(file: File): ImageFrameSet = onCodec(file) { codec ->
        val count = codec.frameCount
        if (count <= 1) return@onCodec ImageFrameSet.Single
        val frames = (0 until count).map { index ->
            ImageFrame(
                index = index,
                kind = if (index == 0) FrameKind.Primary else FrameKind.Sequence,
                durationMs = frameDuration(codec, index)
            )
        }
        ImageFrameSet(frames)
    }

    /**
     * One frame at native resolution.
     *
     * The two-argument `readPixels` is deliberate: skia decodes whatever earlier
     * frames this one is built on by itself. Passing a prior frame would be
     * faster for a forward walk and wrong for every other access pattern, and
     * the strip is walked in both directions.
     */
    fun frame(file: File, frameIndex: Int): ImageBitmap = onCodec(file) { codec ->
        Bitmap().use { bitmap ->
            bitmap.allocPixels(codec.imageInfo)
            codec.readPixels(bitmap, frameIndex)
            bitmap.setImmutable()
            bitmap.asComposeImageBitmap()
        }
    }
}

/**
 * Null rather than zero for a still: [ImageFrame.durationMs] means "this frame
 * is part of an animation and shows for this long", and skia reports `0` both
 * for a frame with no timing and for one asked about out of turn.
 */
private fun frameDuration(codec: Codec, index: Int): Int? =
    runCatching { codec.getFrameInfo(index).duration }.getOrNull()?.takeIf { it > 0 }

/**
 * The encoded bytes are read onto the heap and handed to skia as a [Data].
 * Both it and the [Codec] are closed in reverse order — a codec outliving the
 * data it was built from reads freed memory, and skia does not check.
 */
private fun <T> onCodec(file: File, block: (Codec) -> T): T =
    Data.makeFromBytes(file.readBytes()).use { data ->
        Codec.makeFromData(data).use(block)
    }
