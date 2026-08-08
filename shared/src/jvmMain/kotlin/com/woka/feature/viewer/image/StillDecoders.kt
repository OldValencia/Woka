package com.woka.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.ImageFrameSet
import org.jetbrains.skia.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * One decoder's answer to the only two questions the viewer asks of a still file:
 * what is inside it, and give me frame *n* of it.
 *
 * Three implementations, because no single library reads everything — measured,
 * not assumed (the plan's Phase 0 table). [frameSet] must stay cheap: it counts
 * without decoding, so the strip can decide whether to exist before a pixel is
 * paid for.
 */
internal interface StillDecoder {

    fun frameSet(file: File): ImageFrameSet

    /**
     * The picture's own pixel dimensions, **without decoding it**.
     *
     * The viewer needs this separately from the bitmap because the two stopped
     * agreeing: it draws a small preview first and may hold the picture capped
     * below native, while "Actual size" and every zoom percentage are defined
     * against native 1:1 pixels. Reading it off the bitmap — which is what
     * `ViewerMedia.Still` used to do — makes those wrong for as long as the
     * bitmap is not the full thing.
     *
     * Every decoder can answer cheaply: skia's `Codec`, `ImageIO`'s reader and
     * FFmpeg's stream table all carry dimensions before a pixel is decoded.
     */
    fun nativeSize(file: File, frameIndex: Int): IntSize

    /**
     * Frame [frameIndex] at native resolution. The index is **the decoder's own**
     * — skia counts animation frames, `ImageIO` counts TIFF pages, FFmpeg counts
     * container video streams — so it only ever means anything paired with the
     * decoder that produced it.
     *
     * Throws rather than returning null: `MediaThumbnails` is where every call
     * goes and where a failure becomes a placeholder, and swallowing it here
     * would hide a routing mistake behind a blank tile.
     */
    fun frame(file: File, frameIndex: Int): ImageBitmap
}

/** HEIF is an item collection in a `meta` box; only FFmpeg 7.1+ walks one. */
private val FFMPEG_EXTENSIONS = setOf("heic", "heif", "avif")

/** skia has no TIFF codec, and FFmpeg's `tiff_pipe` reports 1 page for 3. */
private val IMAGE_IO_EXTENSIONS = setOf("tif", "tiff")

/**
 * The routing table — the only place in the app that knows which library reads
 * which format.
 *
 * This is a *decoder* list, not a media list: `MediaKind` still owns the single
 * answer to "is this file media at all", and the two are asked different
 * questions. What keeps them from drifting is `StillDecoderRoutingTest`, which
 * walks every listed image extension through here.
 *
 * skia is the fallback rather than an entry, because it handles everything the
 * app listed before this feature existed. Note what is deliberately *not* routed
 * to FFmpeg: `.webp`, which it opens for a static file and then grabs `0 frames`
 * from an animated one — skia reports all eight with their durations.
 */
internal fun stillDecoderFor(file: File): StillDecoder =
    when (file.extension.lowercase()) {
        in FFMPEG_EXTENSIONS -> FfmpegStillDecoder
        in IMAGE_IO_EXTENSIONS -> ImageIoStillDecoder
        else -> SkiaFrameDecoder
    }

/**
 * A decoded AWT frame handed to Skia, via a PNG re-encode.
 *
 * The round trip is the point, not an oversight: both non-skia decoders produce
 * a `BufferedImage` in whatever layout their library likes — BGR24 off the
 * FFmpeg grabber — while the viewer draws `N32Premul` throughout. Re-encoding is
 * what the video poster path already does and already proved, and it lets Skia
 * do the conversion rather than this file reimplementing it per pixel format.
 */
internal fun BufferedImage.toStillBitmap(): ImageBitmap {
    val stream = ByteArrayOutputStream()
    ImageIO.write(this, "png", stream)
    return Image.makeFromEncoded(stream.toByteArray()).toComposeImageBitmap()
}
