package com.woka.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.FrameKind
import com.woka.core.data.ImageFrame
import com.woka.core.data.ImageFrameSet
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
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
 * (`IllegalArgumentException: Unsupported format`). Those route to
 * [FfmpegStillDecoder] and [ImageIoStillDecoder]; this is the fallback for
 * everything else, and so the decoder every ordinary photograph reaches.
 */
internal object SkiaFrameDecoder : StillDecoder {

    /** Every frame of an animation is the codec's one size. */
    override fun nativeSize(file: File, frameIndex: Int): IntSize =
        onCodec(file) { IntSize(it.width, it.height) }

    /** Frames, kinds and timings — without decoding a single pixel. */
    override fun frameSet(file: File): ImageFrameSet = onCodec(file) { codec ->
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
     * Frame 0 goes through `Image.makeFromEncoded` — the call the viewer has
     * always made — and not through the codec, because the two do not agree:
     * `makeFromEncoded` applies the EXIF `Orientation` tag itself, so a photo
     * shot in portrait comes back in portrait (F3, guarded by
     * `ImageLoaderTest`), while `readPixels` hands over the raster as stored.
     * A frame set never carries EXIF; a photograph routinely does.
     */
    override fun frame(file: File, frameIndex: Int): ImageBitmap =
        if (frameIndex == 0) primary(file) else laterFrame(file, frameIndex)

    // The `readBytes()` copy is unavoidable: skiko 0.144.6 exposes only
    // `makeFromEncoded(ByteArray)` — no `Data` overload, no
    // `makeDeferredFromEncodedData` — so the encoded file is materialised on
    // the heap whatever the caller does (§10.7).
    private fun primary(file: File): ImageBitmap =
        Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()

    /**
     * The two-argument `readPixels` is deliberate: skia decodes whatever earlier
     * frames this one is built on by itself. Passing a prior frame would be
     * faster for a forward walk and wrong for every other access pattern, and
     * the strip is walked in both directions.
     *
     * **The [Bitmap] is deliberately not closed.** `asComposeImageBitmap` wraps
     * it rather than copying it, so the returned [ImageBitmap] *is* this bitmap
     * — closing it frees the pixels out from under the viewer and the next read
     * is an `EXCEPTION_ACCESS_VIOLATION` inside `_nReadPixels`, not an
     * exception. It was written with `use` and crashed the JVM the first time a
     * test read frame 3 of `anim.gif`. Skiko's cleaner frees it once the
     * `ImageBitmap` is unreachable, which is the whole point of handing it over.
     */
    private fun laterFrame(file: File, frameIndex: Int): ImageBitmap = onCodec(file) { codec ->
        val bitmap = Bitmap()
        bitmap.allocPixels(codec.imageInfo)
        codec.readPixels(bitmap, frameIndex)
        bitmap.setImmutable()
        bitmap.asComposeImageBitmap()
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
