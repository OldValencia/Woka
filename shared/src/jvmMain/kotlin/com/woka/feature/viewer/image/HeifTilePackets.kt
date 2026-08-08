package com.woka.feature.viewer.image

import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_clone
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avformat.av_read_frame
import org.bytedeco.ffmpeg.global.avformat.avformat_close_input
import org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info
import org.bytedeco.ffmpeg.global.avformat.avformat_open_input
import org.bytedeco.ffmpeg.global.avutil.av_dict_free
import org.bytedeco.ffmpeg.global.avutil.av_dict_set
import java.io.File

/**
 * Raw `avformat`, because `FFmpegFrameGrabber` decodes exactly one video stream
 * and a tile grid needs sixty to a hundred and forty-four of them.
 *
 * **`avformat_find_stream_info` is deliberately not called**, and that is the
 * single largest saving on this path: it costs 220 ms on a 1296-stream Catalina
 * and buys nothing, because HEIF describes every item — streams, groups,
 * extradata and all — in a `meta` box that `avformat_open_input` has already
 * read. Measured both ways on the same file: identical 9 stream groups,
 * identical pixels out, **1088 ms against 121 ms**. It is what streaming
 * formats need, where the parameters can only be learnt by decoding — and it
 * is exactly why [findStreamInfo] exists: a **PNG** keeps its `cICP` colour
 * tags inside the codec's header, not in a container box, so they appear only
 * once a decoder has looked. HEIF and AVIF need no such thing.
 */
internal fun <T> onFormatContext(
    file: File,
    findStreamInfo: Boolean = false,
    block: (AVFormatContext) -> T
): T {
    val format = AVFormatContext(null)
    val options = AVDictionary(null)
    av_dict_set(options, "max_streams", MAX_STREAMS_OPTION, 0)
    val opened = avformat_open_input(format, file.absolutePath, null, options)
    av_dict_free(options)
    check(opened >= 0) { "avformat_open_input failed with $opened on ${file.name}" }
    return try {
        // Only when the caller says so — see the note above for what it costs
        // and `readStillColor` for the one thing that genuinely needs it.
        if (findStreamInfo) avformat_find_stream_info(format, null as AVDictionary?)
        block(format)
    } finally {
        avformat_close_input(format)
    }
}

/**
 * One demux pass, keeping the first packet of each stream in [streams].
 *
 * Buffering rather than seeking per tile: a HEIF item is a single sample, so
 * this collects one small packet per tile — 144 of them is a few megabytes —
 * and a hundred and forty-four seeks over a 69 MB file is not.
 *
 * The clones are the caller's to free; [releasePackets] does it.
 */
internal fun collectPackets(format: AVFormatContext, streams: Set<Int>): Map<Int, AVPacket> {
    val collected = HashMap<Int, AVPacket>(streams.size)
    val packet = av_packet_alloc()
    try {
        while (collected.size < streams.size && av_read_frame(format, packet) >= 0) {
            val index = packet.stream_index()
            if (index in streams && index !in collected) {
                collected[index] = av_packet_clone(packet)
            }
            av_packet_unref(packet)
        }
    } finally {
        av_packet_free(packet)
    }
    return collected
}

internal fun releasePackets(packets: Map<Int, AVPacket>) {
    packets.values.forEach { av_packet_free(it) }
}
