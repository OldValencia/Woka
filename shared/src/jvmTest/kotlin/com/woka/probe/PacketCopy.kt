package com.woka.probe

import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVRational
import org.bytedeco.ffmpeg.global.avcodec.AV_PKT_FLAG_KEY
import org.bytedeco.ffmpeg.global.avformat.AVSEEK_FLAG_BACKWARD
import org.bytedeco.ffmpeg.global.avformat.av_seek_frame
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.ffmpeg.global.avutil.av_make_q
import org.bytedeco.ffmpeg.global.avutil.av_rescale_q
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import java.io.File

/** Microseconds, as `AVRational` — `AV_TIME_BASE_Q`, which the presets do not expose as a value. */
private val MICROS: AVRational = av_make_q(1, 1_000_000)

/** A stretch of the source in microseconds of its own clock; `untilUs` null runs to the end. */
internal data class Span(val fromUs: Long, val untilUs: Long?)

/**
 * The output side of a copy (ME-290): one recorder, and the bookkeeping a concatenation
 * needs — where the next segment's first keyframe is presented, and the last dts written per
 * input stream so a non-monotonic packet is dropped rather than refused by the muxer.
 */
internal class PacketSink(val recorder: FFmpegFrameRecorder) {
    val lastDts = HashMap<Int, Long>()
    var nextUs = 0L
    var written = 0
    var dropped = 0
}

/**
 * A recorder that **copies** the input's codec parameters instead of opening an encoder:
 * `start(AVFormatContext)` is the overload, and the recorder still wants the frame size
 * and channel count to decide which output streams exist at all.
 */
internal fun copyRecorder(grabber: FFmpegFrameGrabber, target: File, format: String): FFmpegFrameRecorder {
    val recorder = FFmpegFrameRecorder(target, grabber.imageWidth, grabber.imageHeight, grabber.audioChannels)
    recorder.format = format
    recorder.start(grabber.formatContext)
    return recorder
}

/**
 * Copies one span of packets into [sink].
 *
 * The seek is `av_seek_frame` on the grabber's own context, **not** `setTimestamp` —
 * the grabber's seek decodes forward to the exact time, which is a frame operation and
 * would eat packets. Copying starts at the first video keyframe after the seek; audio
 * stamped before it is dropped.
 *
 * With [rebase], every packet is shifted by one amount — the segment's first video
 * keyframe's **pts** moved onto `sink.nextUs`, the presentation end of what is already
 * written — so audio and video keep their offset. Rebasing on dts instead was measured
 * first: on a stream with B-frames it left the output starting 133 ms late. Without
 * [rebase], the source's own stamps go out.
 */
internal fun copySpan(grabber: FFmpegFrameGrabber, sink: PacketSink, span: Span, rebase: Boolean) {
    val context = grabber.formatContext
    if (span.fromUs > 0) av_seek_frame(context, -1, span.fromUs + startOf(grabber), AVSEEK_FLAG_BACKWARD)
    // A plain whole-file copy takes every packet, leading audio included.
    var shiftUs: Long? = if (span.fromUs == 0L && !rebase) 0L else null
    while (true) {
        val packet = grabber.grabPacket() ?: break
        val stream = context.streams(packet.stream_index())
        val type = stream.codecpar().codec_type()
        if (type != AVMEDIA_TYPE_VIDEO && type != AVMEDIA_TYPE_AUDIO) continue
        val base = stream.time_base()
        val video = type == AVMEDIA_TYPE_VIDEO
        val atUs = av_rescale_q(stampOf(packet), base, MICROS)
        if (shiftUs == null) {
            if (!video || packet.flags() and AV_PKT_FLAG_KEY == 0) continue
            shiftUs = if (rebase) atUs - sink.nextUs else 0L
        }
        if (span.untilUs != null && atUs >= span.untilUs + startOf(grabber)) {
            if (video) break else continue
        }
        val shift = av_rescale_q(shiftUs, MICROS, base)
        if (packet.pts() != AV_NOPTS_VALUE) packet.pts(packet.pts() - shift)
        if (packet.dts() != AV_NOPTS_VALUE) packet.dts(packet.dts() - shift)
        val dts = dtsOf(packet)
        val last = sink.lastDts[packet.stream_index()]
        if (last != null && dts <= last) {
            sink.dropped++
            continue
        }
        sink.lastDts[packet.stream_index()] = dts
        if (video) {
            val endUs = av_rescale_q(stampOf(packet) + packet.duration(), base, MICROS)
            sink.nextUs = maxOf(sink.nextUs, endUs)
        }
        sink.recorder.recordPacket(packet)
        sink.written++
    }
}

/** A whole file, packet for packet, into a fresh file of [format]. */
internal fun remuxAll(source: File, target: File, format: String): PacketSink =
    withGrabber(source) { grabber ->
        val sink = PacketSink(copyRecorder(grabber, target, format))
        try {
            copySpan(grabber, sink, Span(0, null), rebase = false)
        } finally {
            sink.recorder.stop()
            sink.recorder.release()
        }
        sink
    }

/** Several spans of one file into one output, in order, each rebased or not. */
internal fun remuxSpans(source: File, target: File, format: String, spans: List<Span>, rebase: Boolean): PacketSink =
    withGrabber(source) { grabber ->
        val sink = PacketSink(copyRecorder(grabber, target, format))
        try {
            spans.forEach { copySpan(grabber, sink, it, rebase) }
        } finally {
            sink.recorder.stop()
            sink.recorder.release()
        }
        sink
    }

internal fun <T> withGrabber(file: File, block: (FFmpegFrameGrabber) -> T): T {
    val grabber = FFmpegFrameGrabber(file)
    try {
        grabber.start()
        return block(grabber)
    } finally {
        runCatching { grabber.stop() }
        runCatching { grabber.release() }
    }
}

/** The container's own start time in µs; `AV_NOPTS_VALUE` reads as zero. */
internal fun startOf(grabber: FFmpegFrameGrabber): Long =
    grabber.formatContext.start_time().takeIf { it != AV_NOPTS_VALUE } ?: 0L

private fun stampOf(packet: AVPacket): Long =
    if (packet.pts() != AV_NOPTS_VALUE) packet.pts() else packet.dts()

private fun dtsOf(packet: AVPacket): Long =
    if (packet.dts() != AV_NOPTS_VALUE) packet.dts() else packet.pts()
