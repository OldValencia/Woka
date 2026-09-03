package com.woka.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.FrameKind
import com.woka.core.data.ImageFrame
import com.woka.core.data.ImageFrameSet
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.awt.image.BufferedImage
import java.io.File
import java.awt.image.DataBufferByte
import java.nio.ByteBuffer

/** A picture whose next eight grabs are all empty is broken, not slow. */
private const val MAX_EMPTY_GRABS = 8

/**
 * HEIF, HEIC and AVIF, through the bundled FFmpeg.
 *
 * These need no new dependency — they needed a *newer* one. Item-based HEIF
 * parsing landed in FFmpeg 7.1, so on the 6.1.1 build the app shipped with,
 * `avformat_open_input` failed on every HEIC with `moov atom not found`. The
 * bump to `ffmpeg 8.0.1-1.5.13` opens them, and AVIF already worked through the
 * `mov` demuxer and the native `av1` decoder even before it.
 *
 * **A picture is a (stream, frame) pair, and neither half can be assumed.**
 * [readStillLayout] is what works out which; this file only turns one of its
 * [FfmpegPicture]s into pixels. `ImageFrame.index` is therefore a position in
 * that layout — the *i*-th picture in the file — and never a raw stream index.
 *
 * **A tiled HEIF is assembled, not grabbed.** Every real-world HEIC measured
 * stores its images as grids of `AV_DISPOSITION_DEPENDENT` tiles, which FFmpeg
 * describes with an `AVStreamGroup` that `FFmpegFrameGrabber` cannot read a
 * frame from at all. Those pictures go to [assembleTileGrid], which decodes the
 * tiles itself and puts them together.
 */
internal object FfmpegStillDecoder : StillDecoder {

    /** Off the layout — a tile grid's display size, or the stream's own. */
    override fun nativeSize(file: File, frameIndex: Int): IntSize =
        readStillLayout(file).pictures.getOrNull(frameIndex)?.size ?: IntSize.Zero

    /** Counted from the demuxer's stream table; nothing is decoded to answer. */
    override fun frameSet(file: File): ImageFrameSet {
        val layout = readStillLayout(file)
        if (layout.pictures.size <= 1) return ImageFrameSet.Single
        return ImageFrameSet(
            layout.pictures.indices.map { position ->
                ImageFrame(
                    index = position,
                    kind = if (position == 0) FrameKind.Primary else FrameKind.Sequence,
                    durationMs = layout.frameDurationMs
                )
            }
        )
    }

    /**
     * Two container opens, deliberately: the layout has to be known before the
     * grabber can be pointed at a stream, and `videoStream` cannot be changed
     * after `start()`. The first open decodes nothing.
     */
    override fun frame(file: File, frameIndex: Int): ImageBitmap = frame(file, frameIndex, 0)

    /**
     * [maxEdge] asks for no more resolution than that, and a tile grid honours
     * it by **assembling smaller** rather than assembling and shrinking: the
     * tiles are being converted anyway, so scaling in the same pass is close to
     * free and the full-size canvas is never allocated. A 6016×6016 preview
     * costs 0.5 MB instead of 138 MB.
     *
     * Ignored by the stream path, where a picture is one decoded frame and
     * there is nothing to save.
     */
    fun frame(file: File, frameIndex: Int, maxEdge: Int): ImageBitmap {
        val layout = readStillLayout(file)
        val picture = layout.pictures.getOrNull(frameIndex)
            ?: error("no picture $frameIndex in ${file.name} (${layout.pictures.size} available)")
        return when (picture) {
            is FfmpegPicture.TileGrid -> assembleTileGrid(file, picture.group, maxEdge)
            is FfmpegPicture.Frame -> onGrabber(file, picture.stream) { grabber ->
                grabAt(grabber, picture.frameInStream).toStillBitmap()
            }
        }
    }
}

/**
 * Grabs forward to [frameInStream] and converts there.
 *
 * The converter's `BufferedImage` shares the JavaCV `Frame`'s buffer, so it is
 * only valid while the grabber is open — hence the conversion happening inside
 * [onGrabber]'s block rather than after it. Walking forward is the only way to
 * reach frame *n* of an animation: seeking a two-second AVIF by timestamp lands
 * on a keyframe, and every frame of one is not a keyframe.
 */
private fun grabAt(grabber: FFmpegFrameGrabber, frameInStream: Int): BufferedImage {
    var reached = -1
    var empty = 0
    while (empty < MAX_EMPTY_GRABS) {
        val frame = grabber.grabImage()
        if (frame?.image == null) {
            empty++
            continue
        }
        empty = 0
        reached++
        if (reached == frameInStream) return frame.toBgrImage()
    }
    error("ran out of frames at $reached looking for $frameInStream")
}

/**
 * The decoded frame as a `TYPE_3BYTE_BGR` image, **taking three channels as a given
 * rather than asking the frame how many it has** (ME-182).
 *
 * `Java2DFrameConverter` infers the channel count as `imageStride / imageWidth`, and
 * ffmpeg pads each row to an alignment. At most widths the division is either exact at
 * three or not exact at all, and the inference survives — but where the padding happens
 * to add exactly one byte per pixel it lands on **four**, and the picture is then read
 * four bytes at a time out of three-byte data. Measured 2026-09-03: a 96-wide frame
 * pads 288 to 384, `384 / 96 == 4`, and the result was the right half black with the
 * rows walking. 128 (`384 / 128 == 3`) and 160 (`576 / 160 == 3.6`) were unaffected,
 * which is why this went unseen — the corpus is 320 and 640 wide.
 *
 * Nothing has to be inferred here: [onGrabber] asks for `AV_PIX_FMT_BGR24` and that is
 * what the buffer holds. The stride is still honoured, because the padding is real.
 */
/** `AV_PIX_FMT_BGR24` is what [onGrabber] asks for, and this is its width in bytes. */
private const val BGR_CHANNELS = 3

private fun Frame.toBgrImage(): BufferedImage {
    val source = image[0] as ByteBuffer
    val target = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_3BYTE_BGR)
    val row = ByteArray(imageWidth * BGR_CHANNELS)
    val into = (target.raster.dataBuffer as DataBufferByte).data

    for (y in 0 until imageHeight) {
        source.position(y * imageStride)
        source.get(row, 0, row.size)
        row.copyInto(into, y * row.size)
    }
    source.rewind()
    return target
}
