package com.woka.feature.viewer.video.engine.decode

import com.woka.feature.viewer.video.engine.probe.UnsupportedVideoException
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_GRAY8
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber.ImageMode

internal data class P5PictureRequest(val mode: ImageMode, val width: Int, val height: Int, val format: Int) {
    fun restore(grabber: FFmpegFrameGrabber) {
        grabber.imageMode = mode
        grabber.imageWidth = width
        grabber.imageHeight = height
        grabber.pixelFormat = format
    }
}

/** Requested public settings stay separate from JavaCV's permanently RAW decoder buffers. */
internal class P5GrabberState(
    var request: P5PictureRequest,
    private val sourceWidth: Int,
    private val sourceHeight: Int
) : AutoCloseable {
    private var colour: DoviColourConverter? = null
    private var scaler: ThreadedVideoConverter? = null
    private var scaledRequest: P5PictureRequest? = null
    private var scaledInput: Pair<Int, Int>? = null

    val width: Int get() = request.width.takeIf { it > 0 && request.mode != ImageMode.RAW } ?: sourceWidth
    val height: Int get() = request.height.takeIf { it > 0 && request.mode != ImageMode.RAW } ?: sourceHeight
    val format: Int get() = when {
        request.mode == ImageMode.RAW -> AV_PIX_FMT_RGBA
        request.format >= 0 -> request.format
        request.mode == ImageMode.GRAY -> AV_PIX_FMT_GRAY8
        else -> AV_PIX_FMT_BGR24
    }

    fun convert(raw: Frame): Frame {
        if (format !in listOf(AV_PIX_FMT_RGBA, AV_PIX_FMT_BGR24, AV_PIX_FMT_GRAY8)) {
            throw UnsupportedVideoException("Dolby Vision Profile 5: unsupported output pixel format.")
        }
        val converter = colour ?: DoviColourConverter().also { colour = it }
        val frame = converter.convert(raw, width, height)
        if (request.mode == ImageMode.RAW || (width == frame.imageWidth && height == frame.imageHeight && format == AV_PIX_FMT_RGBA)) {
            return frame
        }
        val inputSize = frame.imageWidth to frame.imageHeight
        if (scaledRequest != request || scaledInput != inputSize) {
            scaler?.close()
            scaler = null
            scaler = ThreadedVideoConverter(width, height, format)
            scaledRequest = request
            scaledInput = inputSize
        }
        return checkNotNull(scaler).convert(frame.opaque as AVFrame, raw.timestamp).also {
            it.streamIndex = raw.streamIndex
            it.pictType = raw.pictType
            it.type = Frame.Type.VIDEO
        }
    }

    override fun close() {
        try {
            scaler?.close()
            scaler = null
        } finally {
            colour?.close()
            colour = null
        }
    }
}
