package com.woka.feature.editor.save

import androidx.compose.ui.unit.IntSize
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_PAL8
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameFilter
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.File
import java.nio.ByteBuffer

/** GIF counts time in hundredths of a second; the recorder's clock matches it. */
private const val GIF_CLOCK = 100.0

/** The shortest delay GIF can say, and what keeps two frames off the same tick. */
private const val MIN_DELAY_MS = 10

/**
 * A palette per frame, chosen from that frame's own colours, transparency kept: what most
 * GIFs already carry, and a chain that answers one frame per frame — nothing waits for
 * the whole animation, so memory stays at one frame however long the GIF is.
 */
private const val PALETTE_CHAIN =
    "split[a][b];[a]palettegen=stats_mode=single:reserve_transparent=1[p];[b][p]paletteuse=new=1:alpha_threshold=128"

/**
 * Frames in, an animated GIF out, through the bundled ffmpeg — Skia has no GIF encoder.
 *
 * Measured by `GifEncodeProbe` (2026-11-21) before this was written: the fixture's eight
 * frames came back with a mean error of 0, the synthetic transparent hole intact, delays of
 * 50/100/200 ms and the loop count exact. The palette survives JavaCV because the filter
 * copies it after the indices and the recorder's `av_image_fill_arrays` reads it from
 * there. **The clock is ours**: the filter renumbers pts, so each frame is placed at the sum
 * of the delays before it.
 */
internal class GifWriter(
    target: File,
    size: IntSize,
    private val delaysMs: List<Int>,
    repeatCount: Int?
) : AutoCloseable {
    private val width = size.width
    private val height = size.height
    private val starts = mutableListOf<Long>()
    private var nextUs = 0L
    private var written = 0

    private val filter = FFmpegFrameFilter(PALETTE_CHAIN, width, height).apply {
        pixelFormat = AV_PIX_FMT_RGBA
        frameRate = GIF_CLOCK
        start()
    }

    private val recorder = FFmpegFrameRecorder(target, width, height, 0).apply {
        format = "gif"
        videoCodecName = "gif"
        pixelFormat = AV_PIX_FMT_PAL8
        frameRate = GIF_CLOCK
        setOption("loop", loopOption(repeatCount))
        setOption("final_delay", (delayOf(delaysMs.lastIndex) / 10).toString())
        start()
    }

    private val input = Frame(width, height, Frame.DEPTH_UBYTE, 4, width * 4)

    // `also`, not `apply`: inside `apply` the names `width` and `height` are the empty bitmap's own.
    private val pixels = Bitmap().also {
        it.allocPixels(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL))
    }

    /** Straight alpha out: a palette entry is a colour, and premultiplied edges would darken. */
    private val straight = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)

    fun add(image: Image) {
        check(image.readPixels(pixels, 0, 0)) { "the frame could not be drawn for the GIF" }
        val bytes = pixels.readPixels(straight, straight.minRowBytes, 0, 0)
            ?: error("the frame could not be read back")
        val buffer = input.image[0] as ByteBuffer
        buffer.rewind()
        buffer.put(bytes, 0, minOf(bytes.size, buffer.capacity()))
        buffer.rewind()
        starts += nextUs
        input.timestamp = nextUs
        nextUs += delayOf(starts.lastIndex) * 1000L
        filter.push(input, AV_PIX_FMT_RGBA)
        drain()
    }

    /** Every frame that went in has come out; anything else is a broken GIF. */
    fun finish() {
        drain()
        check(written == starts.size) { "the GIF encoder wrote $written of ${starts.size} frames" }
    }

    private fun drain() {
        while (true) {
            val indexed = filter.pullImage() ?: return
            recorder.timestamp = starts[written++]
            recorder.record(indexed, AV_PIX_FMT_PAL8)
        }
    }

    private fun delayOf(index: Int): Int = maxOf(MIN_DELAY_MS, delaysMs.getOrElse(index) { 0 })

    override fun close() {
        runCatching { filter.stop() }
        runCatching { filter.release() }
        runCatching { recorder.stop() }
        runCatching { recorder.release() }
        input.close()
        pixels.close()
    }
}

/** Skia counts repeats after the first pass (-1 forever); GIF's `loop` counts 0 as forever and -1 as once. */
private fun loopOption(repeatCount: Int?): String = when {
    repeatCount == null || repeatCount < 0 -> "0"
    repeatCount == 0 -> "-1"
    else -> repeatCount.toString()
}
