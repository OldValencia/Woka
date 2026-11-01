package com.woka.feature.viewer.video.engine.decode

import org.bytedeco.ffmpeg.avfilter.AVFilterContext
import org.bytedeco.ffmpeg.avfilter.AVFilterGraph
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.avutil.AVRational
import org.bytedeco.ffmpeg.global.avfilter.AV_BUFFERSRC_FLAG_KEEP_REF
import org.bytedeco.ffmpeg.global.avfilter.av_buffersink_get_frame
import org.bytedeco.ffmpeg.global.avfilter.av_buffersink_get_time_base
import org.bytedeco.ffmpeg.global.avfilter.av_buffersrc_add_frame_flags
import org.bytedeco.ffmpeg.global.avfilter.avfilter_get_by_name
import org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_alloc
import org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_config
import org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_create_filter
import org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_free
import org.bytedeco.ffmpeg.global.avfilter.avfilter_graph_parse_ptr
import org.bytedeco.ffmpeg.global.avfilter.avfilter_inout_alloc
import org.bytedeco.ffmpeg.global.avfilter.avfilter_inout_free
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_frame_unref
import org.bytedeco.ffmpeg.global.avutil.av_strdup
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacv.Frame

/** The filter for each number of clockwise quarter turns; none for zero. */
private val TURNS = listOf(null, "transpose=clock", "hflip,vflip", "transpose=cclock")

/**
 * `bwdif`, the display rotation, the render-scale and the RGBA conversion in one
 * libavfilter graph, fed the decoder's own `AVFrame` (VF-102, VF-105).
 *
 * **Not JavaCV's `FFmpegFrameFilter`.** Its `push(Frame)` rebuilds an `AVFrame`
 * from the frame's buffers, which drops the interlaced/TFF flags `bwdif` reads, and
 * on the grabber's RAW planar frames it reads one buffer as three planes and
 * crashes the JVM (measured 2026-11-01, feature doc §7). Pushing the decoded frame
 * itself keeps the flags, so `parity=auto:deint=interlaced` behaves as in the CLI:
 * frames flagged progressive in a mixed stream pass through untouched.
 *
 * Built on the first frame, from that frame's own size and pixel format. Confined
 * to the decoder thread, like the grabber it sits behind.
 */
internal class PictureGraph(
    private val timeBase: AVRational,
    private val outWidth: Int,
    private val outHeight: Int,
    private val deinterlace: Boolean,
    private val clockwiseTurns: Int
) : AutoCloseable {
    private var graph: AVFilterGraph? = null
    private val source = AVFilterContext()
    private val sink = AVFilterContext()
    private val output: AVFrame = av_frame_alloc()

    /**
     * Pushes [decoded] and returns the next RGBA picture, or null while `bwdif`
     * waits for the frame after it. The returned [Frame] borrows this graph's
     * buffer and is valid until the next call.
     */
    fun process(decoded: AVFrame): Frame? {
        if (graph == null) build(decoded)
        if (decoded.pts() == AV_NOPTS_VALUE) decoded.pts(decoded.best_effort_timestamp())
        check(av_buffersrc_add_frame_flags(source, decoded, AV_BUFFERSRC_FLAG_KEEP_REF) >= 0) {
            "picture graph: push rejected"
        }
        return pull()
    }

    /** End of stream: the frame `bwdif` held back for a neighbour that never came. */
    fun drain(): Frame? {
        if (graph == null) return null
        av_buffersrc_add_frame_flags(source, null, 0)
        return pull()
    }

    private fun pull(): Frame? {
        av_frame_unref(output)
        if (av_buffersink_get_frame(sink, output) < 0) return null
        val stride = output.linesize(0)
        val height = output.height()
        val frame = Frame()
        frame.imageWidth = output.width()
        frame.imageHeight = height
        frame.imageDepth = Frame.DEPTH_UBYTE
        frame.imageChannels = 4
        frame.imageStride = stride
        frame.image = arrayOf(output.data(0).capacity(stride.toLong() * height).asByteBuffer())
        // The sink's own time base: `bwdif` halves the input's so a field could
        // have a time of its own, which read in the source's doubled every time.
        val base = av_buffersink_get_time_base(sink)
        frame.timestamp = output.pts() * 1_000_000L * base.num() / base.den()
        return frame
    }

    private fun build(first: AVFrame) {
        val built = avfilter_graph_alloc()
        graph = built
        val ratio = first.sample_aspect_ratio()
        val aspect = if (ratio.num() > 0) "${ratio.num()}/${ratio.den()}" else "1/1"
        val args = "video_size=${first.width()}x${first.height()}:pix_fmt=${first.format()}:" +
            "time_base=${timeBase.num()}/${timeBase.den()}:pixel_aspect=$aspect"
        check(avfilter_graph_create_filter(source, avfilter_get_by_name("buffer"), "in", args, null, built) >= 0) {
            "picture graph: buffer source rejected $args"
        }
        check(avfilter_graph_create_filter(sink, avfilter_get_by_name("buffersink"), "out", null, null, built) >= 0) {
            "picture graph: buffer sink rejected"
        }
        val outputs = avfilter_inout_alloc()
        val inputs = avfilter_inout_alloc()
        try {
            outputs.name(av_strdup(BytePointer("in"))).filter_ctx(source).pad_idx(0).next(null)
            inputs.name(av_strdup(BytePointer("out"))).filter_ctx(sink).pad_idx(0).next(null)
            check(avfilter_graph_parse_ptr(built, chain(), inputs, outputs, null) >= 0) {
                "picture graph: graph rejected ${chain()}"
            }
            check(avfilter_graph_config(built, null) >= 0) { "picture graph: graph config failed" }
        } finally {
            avfilter_inout_free(inputs)
            avfilter_inout_free(outputs)
        }
    }

    /**
     * `bwdif` first, on the fields as they were shot; then the turn the stream's
     * display matrix asks for (VF-105) — matched against the CLI's own autorotate,
     * PSNR inf on all three; then `scale`, only when the render-scale asked for a
     * size, which is already in the turned orientation.
     */
    private fun chain(): String = listOfNotNull(
        "bwdif=mode=send_frame:parity=auto:deint=interlaced".takeIf { deinterlace },
        TURNS[clockwiseTurns],
        "scale=$outWidth:$outHeight".takeIf { outWidth > 0 && outHeight > 0 },
        "format=rgba"
    ).joinToString(",")

    override fun close() {
        av_frame_free(output)
        graph?.let { avfilter_graph_free(it) }
        graph = null
    }
}
