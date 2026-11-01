package com.woka.feature.viewer.video.engine.probe

import com.woka.feature.viewer.image.onFormatContext
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avutil.AVRational
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avformat.AVSEEK_FLAG_BYTE
import org.bytedeco.ffmpeg.global.avformat.AV_DISPOSITION_ATTACHED_PIC
import org.bytedeco.ffmpeg.global.avformat.av_read_frame
import org.bytedeco.ffmpeg.global.avformat.avformat_seek_file
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import java.io.File

/**
 * How far before the end the tail read starts. Enough for several GOPs of 1080p
 * CCTV H.264 — the last packet only has to be in there — and small enough that the
 * read costs the same on a 440 MB `.dav` as on a 300 KB one (VF-001 §3).
 */
private const val TAIL_BYTES = 4L * 1024 * 1024

/**
 * Demuxers whose packet times are frame counters kept while reading. After a byte
 * seek they count from the landing point, so the tail gives the wrong end —
 * measured 2026-11-01: `.mtv` 19.7 s of 93 s, a 900 s `.amv` 9.6 s. These are
 * walked whole; `avi` is the demuxer `.amv` goes through.
 */
private val COUNTING_DEMUXERS = setOf("avi", "mtv")

/**
 * The length of a video whose container does not state one — `.nsv`, `.mtv`,
 * `.amv` (VF-001, probe table 2026-11-01) — **counted, not estimated**. Called
 * through `lengthMs`, only where the container states no length or is `dhav`.
 *
 * The end is the last video packet's `pts + duration`, found by seeking to the
 * file's last [TAIL_BYTES] by byte and demuxing to EOF without decoding; the start
 * is the stream's `start_time`, or its first packet when the container has none
 * (`.mtv`). A bitrate × size guess was rejected: it is a heuristic no test can pin
 * (feature doc §6). [COUNTING_DEMUXERS], and a demuxer that refuses the byte seek,
 * are walked from the start instead.
 *
 * Null when there is no video stream or nothing timed was found.
 */
internal fun measuredDurationMs(file: File): Long? = runCatching {
    onFormatContext(file) { format ->
        val demuxer = format.iformat().name().string
        val first = firstVideoPacket(format) ?: return@onFormatContext null
        val declared = format.streams(first.stream).start_time()
        val start = if (declared != AV_NOPTS_VALUE) declared else first.pts
        // A failed byte seek leaves the demuxer where it stands, just past the
        // first packet, so the read below becomes the full walk.
        if (demuxer !in COUNTING_DEMUXERS) {
            val tail = maxOf(0L, file.length() - TAIL_BYTES)
            avformat_seek_file(format, -1, Long.MIN_VALUE, tail, Long.MAX_VALUE, AVSEEK_FLAG_BYTE)
        }
        val end = lastPacketEnd(format, first.stream, first.pts)
        toMs(end - start, format.streams(first.stream).time_base())
    }
}.getOrNull()?.takeIf { it > 0 }

private class TimedPacket(val stream: Int, val pts: Long)

private fun isVideo(format: AVFormatContext, index: Int): Boolean {
    val stream = format.streams(index)
    return stream.codecpar().codec_type() == AVMEDIA_TYPE_VIDEO &&
        (stream.disposition() and AV_DISPOSITION_ATTACHED_PIC) == 0
}

/**
 * The first video packet that carries a time. Read rather than looked up because
 * `dhav` declares no streams in its header — they appear with their first packet,
 * so the stream table of a freshly opened `.dav` is empty.
 */
private fun firstVideoPacket(format: AVFormatContext): TimedPacket? {
    val packet = av_packet_alloc()
    try {
        while (av_read_frame(format, packet) >= 0) {
            val index = packet.stream_index()
            val timed = packet.pts() != AV_NOPTS_VALUE && isVideo(format, index)
            val found = if (timed) TimedPacket(index, packet.pts()) else null
            av_packet_unref(packet)
            if (found != null) return found
        }
        return null
    } finally {
        av_packet_free(packet)
    }
}

private fun lastPacketEnd(format: AVFormatContext, stream: Int, from: Long): Long {
    val packet = av_packet_alloc()
    var end = from
    try {
        while (av_read_frame(format, packet) >= 0) {
            val pts = packet.pts()
            if (packet.stream_index() == stream && pts != AV_NOPTS_VALUE) {
                end = maxOf(end, pts + maxOf(0L, packet.duration()))
            }
            av_packet_unref(packet)
        }
    } finally {
        av_packet_free(packet)
    }
    return end
}

private fun toMs(ticks: Long, timeBase: AVRational): Long =
    ticks * 1000L * timeBase.num() / timeBase.den()
