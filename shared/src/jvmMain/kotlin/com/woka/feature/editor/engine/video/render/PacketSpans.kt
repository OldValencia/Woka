package com.woka.feature.editor.engine.video.render

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

/** `AV_TIME_BASE_Q`, which the presets do not expose as a value. */
private val MICROS: AVRational = av_make_q(1, 1_000_000)

/**
 * The output side of a copy (ME-291): one recorder, where the next segment's first
 * keyframe is to be presented, and the last dts written per input stream — a packet that
 * would go backwards is dropped rather than refused by the muxer. It never fired in the
 * probe, and it is kept because a refused packet is a failed render.
 */
internal class PacketSink(val recorder: FFmpegFrameRecorder) {
    val lastDts = HashMap<Int, Long>()
    var nextUs = 0L
}

/**
 * Copies source `[fromUs, untilUs)` into [sink], rebased so it starts where the previous
 * segment ended — **ME-290's measured recipe, moved from the probe unchanged**:
 *
 * - the seek is `av_seek_frame` on the grabber's own context, never `setTimestamp`, which
 *   decodes forward and would eat packets;
 * - copying starts at the first video keyframe after it, and audio stamped before it is
 *   dropped — which is why a copy is only offered when every clip starts on one;
 * - one shift per segment, anchored on that keyframe's **pts**: anchoring on dts left an
 *   HEVC clip with B-frames starting 133 ms late.
 *
 * [onPacket] gets each written packet's source time in µs, for the progress bar. [alive]
 * is asked per packet, so a cancel lands within one packet.
 */
internal fun copySpan(
    grabber: FFmpegFrameGrabber,
    sink: PacketSink,
    fromUs: Long,
    untilUs: Long,
    alive: () -> Boolean,
    onPacket: (Long) -> Unit
) {
    val context = grabber.formatContext
    val start = containerStartUs(grabber)
    // **Not on a fresh grabber at 0**, which is where it already is: measured 2026-11-01, a
    // Dahua `.dav` — no index, its start a wall-clock epoch — copied nothing at all after a
    // seek to its own start. A grabber that has already been read has to go back, though.
    if (fromUs > 0L || sink.nextUs > 0L) av_seek_frame(context, -1, fromUs + start, AVSEEK_FLAG_BACKWARD)
    var shiftUs: Long? = null
    while (alive()) {
        val packet = grabber.grabPacket() ?: break
        val stream = context.streams(packet.stream_index())
        val type = stream.codecpar().codec_type()
        if (type != AVMEDIA_TYPE_VIDEO && type != AVMEDIA_TYPE_AUDIO) continue
        val base = stream.time_base()
        val video = type == AVMEDIA_TYPE_VIDEO
        val atUs = av_rescale_q(stampOf(packet), base, MICROS)
        if (shiftUs == null) {
            if (!video || packet.flags() and AV_PKT_FLAG_KEY == 0) continue
            // `AVSEEK_FLAG_BACKWARD` lands on the keyframe at or *before* the cut, and the copy
            // is allowed for one up to 16 ms after it — so the GOP the user cut away was copied
            // in front of the clip (1.0.6 review). The span starts on the keyframe at the cut.
            if (atUs < fromUs + start) continue
            shiftUs = atUs - sink.nextUs
        }
        if (atUs >= untilUs + start) {
            if (video) break else continue
        }
        val shift = av_rescale_q(shiftUs, MICROS, base)
        if (packet.pts() != AV_NOPTS_VALUE) packet.pts(packet.pts() - shift)
        if (packet.dts() != AV_NOPTS_VALUE) packet.dts(packet.dts() - shift)
        val dts = dtsOf(packet)
        val last = sink.lastDts[packet.stream_index()]
        if (last != null && dts <= last) continue
        sink.lastDts[packet.stream_index()] = dts
        if (video) {
            val endUs = av_rescale_q(stampOf(packet) + packet.duration(), base, MICROS)
            sink.nextUs = maxOf(sink.nextUs, endUs)
        }
        sink.recorder.recordPacket(packet)
        onPacket(atUs - start)
    }
}

/**
 * Every video keyframe's presentation time, in ms from the container's start — read off
 * packet flags with nothing decoded, 2–4 ms for a short file (ME-290). The grabber is left
 * at the end; whoever copies seeks first anyway.
 */
internal fun keyframesMs(grabber: FFmpegFrameGrabber, alive: () -> Boolean = { true }): List<Long> {
    val context = grabber.formatContext
    val start = containerStartUs(grabber)
    val keys = ArrayList<Long>()
    // Asked per packet: on a multi-GB film this walk is seconds, and ✕ waited it out (1.0.6 review).
    while (alive()) {
        val packet = grabber.grabPacket() ?: break
        val stream = context.streams(packet.stream_index())
        if (stream.codecpar().codec_type() != AVMEDIA_TYPE_VIDEO) continue
        if (packet.flags() and AV_PKT_FLAG_KEY == 0) continue
        keys += (av_rescale_q(stampOf(packet), stream.time_base(), MICROS) - start) / 1000L
    }
    return keys
}

/** The container's own start time in µs; `AV_NOPTS_VALUE` reads as zero. */
internal fun containerStartUs(grabber: FFmpegFrameGrabber): Long =
    grabber.formatContext.start_time().takeIf { it != AV_NOPTS_VALUE } ?: 0L

private fun stampOf(packet: AVPacket): Long =
    if (packet.pts() != AV_NOPTS_VALUE) packet.pts() else packet.dts()

private fun dtsOf(packet: AVPacket): Long =
    if (packet.dts() != AV_NOPTS_VALUE) packet.dts() else packet.pts()
