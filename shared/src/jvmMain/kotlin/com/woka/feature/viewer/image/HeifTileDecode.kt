package com.woka.feature.viewer.image

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_to_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free

/** One decoded tile as BGRA bytes, in the byte order Skia's `BGRA_8888` wants. */
internal class DecodedTile(val width: Int, val height: Int, val bgra: ByteArray)

/**
 * Everything one tile needs to decode, resolved off the container **before**
 * any decoding starts.
 *
 * The separation is what lets the tiles decode in parallel: `AVFormatContext`
 * hands out its streams and their parameters through position-carrying JavaCPP
 * pointers, and reading those from a dozen threads at once is not a question
 * worth having to answer. Resolved on one thread, decoded on many.
 */
internal class TileJob(
    val tile: HeifTile,
    val codecpar: AVCodecParameters,
    val packet: AVPacket
)

/**
 * Decodes one tile: a fresh codec context, opened and closed around a single
 * packet.
 *
 * Per-tile rather than one shared context because each HEIF item carries its
 * own `hvcC` extradata and nothing guarantees they match — and because a
 * context per tile is what makes decoding them in parallel safe at all.
 *
 * The null `send_packet` is the flush that makes the decoder hand the frame
 * over: an intra-only tile is not emitted until the decoder is told no more
 * input is coming.
 */
internal fun decodeTile(job: TileJob, shrinkBy: Int = 1): DecodedTile {
    val codec = avcodec_find_decoder(job.codecpar.codec_id())
        ?: error("no decoder for tile stream ${job.tile.stream}")
    val context = avcodec_alloc_context3(codec)
    val frame = av_frame_alloc()
    try {
        check(avcodec_parameters_to_context(context, job.codecpar) >= 0) { "tile codecpar copy failed" }
        check(avcodec_open2(context, codec, null as AVDictionary?) >= 0) { "tile decoder open failed" }
        avcodec_send_packet(context, job.packet)
        avcodec_send_packet(context, null)
        val received = avcodec_receive_frame(context, frame)
        check(received >= 0) { "tile stream ${job.tile.stream} decoded nothing ($received)" }
        return frame.toBgraTile("tile ${job.tile.stream}", shrinkBy)
    } finally {
        av_frame_free(frame)
        avcodec_free_context(context)
    }
}
