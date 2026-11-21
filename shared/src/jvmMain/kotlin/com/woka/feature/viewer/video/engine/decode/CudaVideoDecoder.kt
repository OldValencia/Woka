package com.woka.feature.viewer.video.engine.decode

import com.woka.feature.viewer.video.engine.sync.originUs
import kotlinx.coroutines.CancellationException
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_flush_buffers
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet
import org.bytedeco.ffmpeg.global.avformat.AVSEEK_FLAG_BACKWARD
import org.bytedeco.ffmpeg.global.avformat.av_read_frame
import org.bytedeco.ffmpeg.global.avformat.avformat_seek_file
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EAGAIN
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_CUDA
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_frame_unref

/**
 * Single-owner packet reader sharing only JavaCV's public demuxer (EK-002).
 * Always receives before sending: FFmpeg forbids EAGAIN on both sides, so no packet
 * queue is needed. EOF drains delayed pictures; seek flushes them before reading again.
 * One borrowed picture survives until the next read/seek/close, including a seek landing.
 */
internal class CudaVideoDecoder(
    private val format: AVFormatContext,
    private val streamIndex: Int,
    private val alive: () -> Boolean
) : AutoCloseable {
    private var codec: CudaVideoCodec? = null
    private var packet: AVPacket? = null
    private var picture: AVFrame? = null
    private var draining = false
    private var pending = false
    private var closed = false
    var timestampUs = format.originUs
        private set

    init {
        try {
            codec = CudaVideoCodec.open(format.streams(streamIndex))
            packet = av_packet_alloc()
            picture = av_frame_alloc()
            check(packet != null && !packet!!.isNull && picture != null && !picture!!.isNull)
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    fun next(): AVFrame? {
        check(!closed)
        if (!alive()) throw CancellationException("Video decoder cancelled")
        if (pending) {
            pending = false
            return picture
        }
        val context = checkNotNull(codec).context
        val output = checkNotNull(picture)
        val input = checkNotNull(packet)
        while (true) {
            if (!alive()) throw CancellationException("Video decoder cancelled")
            val received = avcodec_receive_frame(context, output)
            if (received == 0) {
                check(output.format() == AV_PIX_FMT_CUDA) { "CUDA decoder returned software pixels" }
                val pts = output.best_effort_timestamp().takeIf { it != AV_NOPTS_VALUE } ?: output.pts()
                check(pts != AV_NOPTS_VALUE) { "CUDA picture has no timestamp" }
                val base = format.streams(streamIndex).time_base()
                timestampUs = pts * 1_000_000L * base.num() / base.den()
                return output
            }
            if (received == AVERROR_EOF) return null
            check(received == AVERROR_EAGAIN() && !draining) { "CUDA receive failed ($received)" }
            val read = av_read_frame(format, input)
            try {
                if (read == AVERROR_EOF) {
                    check(avcodec_send_packet(context, null) >= 0) { "CUDA drain failed" }
                    draining = true
                } else {
                    check(read >= 0) { "Video packet read failed ($read)" }
                    if (input.stream_index() == streamIndex) {
                        val sent = avcodec_send_packet(context, input)
                        check(sent >= 0) { "CUDA packet rejected ($sent)" }
                    }
                }
            } finally {
                av_packet_unref(input)
            }
        }
    }

    fun seek(mediaUs: Long) {
        check(!closed)
        val target = mediaUs.coerceAtLeast(0L) + format.originUs
        val result = avformat_seek_file(format, -1, Long.MIN_VALUE, target, target, AVSEEK_FLAG_BACKWARD)
        check(result >= 0) { "CUDA demux seek failed ($result)" }
        avcodec_flush_buffers(checkNotNull(codec).context)
        av_packet_unref(packet)
        av_frame_unref(picture)
        draining = false
        pending = false
        // JavaCV also bounds its seek walk at 1000 frames; the caller can continue walking (VF-001).
        repeat(1000) {
            if (next() == null) return
            if (timestampUs >= target - 1) {
                pending = true
                return
            }
        }
        pending = true
    }

    override fun close() {
        if (closed) return
        closed = true
        picture?.let { if (!it.isNull) av_frame_free(it) }
        packet?.let { if (!it.isNull) av_packet_free(it) }
        codec?.close()
        picture = null
        packet = null
        codec = null
    }
}
