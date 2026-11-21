package com.woka.feature.viewer.video.engine.preview

import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.probe.requireSupportedVideo
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File

/** Wide enough for an L card at 125 % scaling; the decode cost barely moves with it. */
private const val OUT_WIDTH = 320

/** The seek preview's budget: measured to keep 1.6× ahead of real time on a 2160p remux. */
private const val THREADS = "2"

/**
 * Opened twice: once to learn the picture's shape, then at [OUT_WIDTH] with that
 * shape. The output size is fixed before `start()`, and reading it back after
 * assigning one side returns the value written (`SeekPreviewSource.applyPreviewSize`).
 * The second open is cheap because the probe is capped. Unsupported signals are
 * rejected before the second open; every failed open releases the grabber too.
 */
internal fun openHoverGrabber(file: File): FFmpegFrameGrabber? {
    val grabber = FreshFrameGrabber(file)
    return try {
        grabber.setOption("probesize", "2000000")
        grabber.setOption("analyzeduration", "1000000")
        grabber.setVideoOption("threads", THREADS)
        grabber.pixelFormat = AV_PIX_FMT_RGBA
        grabber.audioStream = Int.MAX_VALUE
        grabber.start()
        grabber.requireSupportedVideo()
        val sourceWidth = grabber.imageWidth.coerceAtLeast(1)
        val sourceHeight = grabber.imageHeight
        grabber.stop()
        grabber.imageWidth = OUT_WIDTH
        val scaled = (OUT_WIDTH.toLong() * sourceHeight / sourceWidth).toInt()
        grabber.imageHeight = (scaled - scaled % 2).coerceAtLeast(2)
        grabber.start()
        grabber
    } catch (_: Exception) {
        runCatching { grabber.stop() }
        runCatching { grabber.release() }
        null
    }
}
