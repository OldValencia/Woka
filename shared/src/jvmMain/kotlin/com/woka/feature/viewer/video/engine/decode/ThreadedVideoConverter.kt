package com.woka.feature.viewer.video.engine.decode

import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_CUDA
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_copy_props
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_buffer
import org.bytedeco.ffmpeg.global.avutil.av_frame_remove_side_data
import org.bytedeco.ffmpeg.global.avutil.av_hwframe_transfer_data
import org.bytedeco.ffmpeg.global.avutil.av_opt_set_int
import org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR
import org.bytedeco.ffmpeg.global.swscale.sws_alloc_context
import org.bytedeco.ffmpeg.global.swscale.sws_freeContext
import org.bytedeco.ffmpeg.global.swscale.sws_init_context
import org.bytedeco.ffmpeg.global.swscale.sws_scale_frame
import org.bytedeco.javacv.Frame

/**
 * Owns one download buffer, one RGBA buffer and a four-thread scaler (EK-001/002).
 * Legacy swscale setup keeps JavaCV's colour coefficients: the dynamic API changes them.
 * Measured 2026-11-12: 24 matching pixel/metadata comparisons; ~69–79 fps with bitmap copy.
 * Output is borrowed until the next conversion or close; the existing presenter copies it.
 */
internal class ThreadedVideoConverter(private val width: Int, private val height: Int) : AutoCloseable {
    private var downloaded: AVFrame? = null
    private var output: AVFrame? = null
    private var scaler: SwsContext? = null
    private val frame = Frame()
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var sourceFormat = -1
    private var sourceHardwareFormat = -1
    private var closed = false

    init {
        try {
            downloaded = av_frame_alloc()
            output = av_frame_alloc()
            scaler = sws_alloc_context()
            check(downloaded != null && !downloaded!!.isNull)
            check(output != null && !output!!.isNull && scaler != null && !scaler!!.isNull)
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    fun convert(source: AVFrame, timestampUs: Long): Frame {
        check(!closed)
        if (sourceWidth != 0) {
            check(source.width() == sourceWidth && source.height() == sourceHeight &&
                source.format() == sourceHardwareFormat) { "Video geometry/format changed" }
        }
        val input = if (source.format() == AV_PIX_FMT_CUDA) {
            check(av_hwframe_transfer_data(downloaded, source, 0) >= 0) { "CUDA download failed" }
            checkNotNull(downloaded)
        } else source
        if (sourceWidth == 0) initialise(input, source.format())
        check(input.format() == sourceFormat) { "Download format changed" }
        check(sws_scale_frame(scaler, output, input) >= 0) { "RGBA conversion failed" }
        val converted = checkNotNull(output)
        while (converted.nb_side_data() > 0) {
            av_frame_remove_side_data(converted, converted.side_data(0).type())
        }
        check(av_frame_copy_props(converted, source) >= 0) { "Video metadata copy failed" }
        frame.timestamp = timestampUs
        frame.keyFrame = source.flags() and AVFrame.AV_FRAME_FLAG_KEY != 0
        return frame
    }

    private fun initialise(input: AVFrame, hardwareFormat: Int) {
        val options = mapOf(
            "srcw" to input.width(), "srch" to input.height(), "src_format" to input.format(),
            "dstw" to width, "dsth" to height, "dst_format" to AV_PIX_FMT_RGBA,
            "sws_flags" to SWS_BILINEAR, "threads" to 4
        )
        for ((key, value) in options) check(av_opt_set_int(scaler, key, value.toLong(), 0) >= 0)
        check(sws_init_context(scaler, null, null) >= 0)
        val converted = checkNotNull(output)
        converted.width(width).height(height).format(AV_PIX_FMT_RGBA)
        check(av_frame_get_buffer(converted, 32) >= 0)
        frame.imageWidth = width
        frame.imageHeight = height
        frame.imageDepth = Frame.DEPTH_UBYTE
        frame.imageChannels = 4
        frame.imageStride = converted.linesize(0)
        frame.image = arrayOf(converted.data(0).capacity(frame.imageStride.toLong() * height).asByteBuffer())
        frame.opaque = converted
        sourceWidth = input.width()
        sourceHeight = input.height()
        sourceFormat = input.format()
        sourceHardwareFormat = hardwareFormat
    }

    override fun close() {
        if (closed) return
        closed = true
        scaler?.let { if (!it.isNull) sws_freeContext(it) }
        output?.let { if (!it.isNull) av_frame_free(it) }
        downloaded?.let { if (!it.isNull) av_frame_free(it) }
        scaler = null
        output = null
        downloaded = null
        frame.image = null
        frame.opaque = null
    }
}
