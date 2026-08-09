package com.woka.feature.viewer.video.engine.preview

import androidx.compose.ui.graphics.ImageBitmap
import com.woka.feature.viewer.video.engine.decode.toComposeBitmap
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File

/** Wide enough for the 168dp pill at 150% scaling, and not one pixel wider. */
private const val PREVIEW_WIDTH = 240

/** Below `auto` on purpose: a preview must not take every core from 4K HEVC. */
private const val PREVIEW_THREADS = "2"

/**
 * Decodes one still at a time, for the seek bar's hover preview.
 *
 * Constructed twice — once with [keyframesOnly] for the coarse grid, once without
 * for the exact refine. Measured on a 2160p HEVC file, keyframes-only lands a frame
 * in 117–156ms against 155–271ms; the coarse tier carries the sweep so the faster
 * one matters there, and the exact tier cannot use it because it has to land on a
 * non-keyframe. Both are driven from the controller's single preview thread, so two
 * sources still mean one decode in flight.
 *
 * Nothing here is registered with `FrameRetention`: these stills are retained by the
 * preview cache, not by the play head.
 */
internal class SeekPreviewSource(
    private val file: File,
    private val keyframesOnly: Boolean,
    private val sourceWidth: Int,
    private val sourceHeight: Int
) {
    private var grabber: FFmpegFrameGrabber? = null
    private var unusable = false

    /** Blocking; callers are already on the preview thread. Null on any failure. */
    fun frameAt(positionMs: Long): ImageBitmap? {
        val open = grabber ?: openGrabber() ?: return null
        return try {
            open.setTimestamp(positionMs * 1000L)
            open.grabImage()?.takeIf { it.image != null }?.toComposeBitmap()
        } catch (_: Exception) {
            null
        }
    }

    fun close() {
        val open = grabber ?: return
        grabber = null
        try {
            open.stop()
            open.release()
        } catch (_: Exception) {
        }
    }

    private fun openGrabber(): FFmpegFrameGrabber? {
        if (unusable) return null
        return try {
            FFmpegFrameGrabber(file).apply {
                // The player's own grabber already enumerated the streams, so this
                // one needs only enough of a probe to decode: 4ms against 59ms.
                setOption("probesize", "2000000")
                setOption("analyzeduration", "1000000")
                setVideoOption("threads", PREVIEW_THREADS)
                if (keyframesOnly) setVideoOption("skip_frame", "nokey")
                pixelFormat = AV_PIX_FMT_RGBA
                audioStream = Int.MAX_VALUE // no stream matches, so none is opened
                applyPreviewSize()
                start()
            }.also { grabber = it }
        } catch (_: Exception) {
            // One failure is enough; retrying per hover would stall the pointer.
            unusable = true
            null
        }
    }

    /**
     * Output size is fixed before `start()`, which is why the aspect comes from the
     * caller's track data rather than from the grabber: reading `imageHeight` back
     * after assigning `imageWidth` returns the value just written, and the frame
     * comes out at the full height of the source. That mistake is exactly what the
     * cost probe for this feature did.
     */
    private fun FFmpegFrameGrabber.applyPreviewSize() {
        if (sourceWidth <= 0 || sourceHeight <= 0) return
        imageWidth = PREVIEW_WIDTH
        imageHeight = even(PREVIEW_WIDTH.toLong() * sourceHeight / sourceWidth)
    }

    private fun even(value: Long): Int = (value - value % 2).toInt().coerceAtLeast(2)
}
