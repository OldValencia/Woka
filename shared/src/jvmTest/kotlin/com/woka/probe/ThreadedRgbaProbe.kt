package com.woka.probe

import org.bytedeco.ffmpeg.avutil.AVFrame
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
 * TEMPORARY (EK-001): bounded GPU download and legacy-compatible threaded RGBA conversion.
 * One owned download buffer and one output frame; callers must copy pixels before the next call.
 * Explicit legacy setup preserves JavaCV's existing colour coefficients rather than silently
 * changing its colour policy through the new dynamic `sws_scale_frame` API.
 */
internal class ThreadedRgbaProbe(
    private val width: Int,
    private val height: Int,
    private val threads: Int
) : AutoCloseable {
    private val downloaded = av_frame_alloc()
    val output: AVFrame = av_frame_alloc()
    private val scaler = sws_alloc_context()
    private var ready = false

    fun convert(source: AVFrame): Frame {
        val input = if (source.format() == AV_PIX_FMT_CUDA) {
            check(av_hwframe_transfer_data(downloaded, source, 0) >= 0) { "GPU download failed" }
            downloaded
        } else source
        if (!ready) initialise(input)
        check(sws_scale_frame(scaler, output, input) >= 0) { "Threaded conversion failed" }
        while (output.nb_side_data() > 0) av_frame_remove_side_data(output, output.side_data(0).type())
        check(av_frame_copy_props(output, source) >= 0)
        return Frame().apply {
            imageWidth = output.width()
            imageHeight = output.height()
            imageDepth = Frame.DEPTH_UBYTE
            imageChannels = 4
            imageStride = output.linesize(0)
            image = arrayOf(output.data(0).capacity(imageStride.toLong() * imageHeight).asByteBuffer())
            opaque = output
        }
    }

    private fun initialise(input: AVFrame) {
        val options = mapOf(
            "srcw" to input.width(), "srch" to input.height(), "src_format" to input.format(),
            "dstw" to width, "dsth" to height, "dst_format" to AV_PIX_FMT_RGBA,
            "sws_flags" to SWS_BILINEAR, "threads" to threads
        )
        for ((key, value) in options) check(av_opt_set_int(scaler, key, value.toLong(), 0) >= 0)
        check(sws_init_context(scaler, null, null) >= 0)
        output.width(width).height(height).format(AV_PIX_FMT_RGBA)
        check(av_frame_get_buffer(output, 32) >= 0)
        ready = true
    }

    override fun close() {
        sws_freeContext(scaler)
        av_frame_free(output)
        av_frame_free(downloaded)
    }
}
