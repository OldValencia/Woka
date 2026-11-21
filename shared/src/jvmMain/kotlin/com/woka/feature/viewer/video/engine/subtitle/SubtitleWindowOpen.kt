package com.woka.feature.viewer.video.engine.subtitle

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avformat.AVStream
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.global.avcodec.AVDISCARD_ALL
import org.bytedeco.ffmpeg.global.avcodec.AVDISCARD_DEFAULT
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_to_context
import org.bytedeco.ffmpeg.global.avformat.AVSEEK_FLAG_BACKWARD
import org.bytedeco.ffmpeg.global.avformat.av_seek_frame
import org.bytedeco.ffmpeg.global.avformat.avformat_close_input
import org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info
import org.bytedeco.ffmpeg.global.avformat.avformat_open_input
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_SUBTITLE
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import com.woka.feature.viewer.video.engine.sync.originUs

/**
 * Opens embedded subtitle track [index] of [filepath] for reading in windows, or null when
 * the file does not open or the stream is not a decodable subtitle. Every other stream is
 * discarded, so the demuxer hands over this track's packets alone. [bitmap] — a PGS or
 * VobSub track — gets a second decoder to draw with (see [SubtitleWindow]).
 */
internal fun openSubtitleWindow(filepath: String, index: Int, bitmap: Boolean): SubtitleWindow? {
    val fc = AVFormatContext(null)
    if (avformat_open_input(fc, filepath, null, null) != 0) return null
    val stream = fc.takeIf { avformat_find_stream_info(it, null as AVDictionary?) >= 0 }
        ?.takeIf { index < it.nb_streams() }
        ?.streams(index)
    val indexCtx = stream?.openSubtitleCodec()
    if (stream == null || indexCtx == null) {
        avformat_close_input(fc)
        return null
    }
    val clock = fc.clockStream(index)
    for (i in 0 until fc.nb_streams()) {
        if (i != index && i != clock) fc.streams(i)?.discard(AVDISCARD_ALL)
    }
    val drawCtx = if (bitmap) stream.openSubtitleCodec() else null
    return SubtitleWindow(fc, index, clock, stream, indexCtx, drawCtx)
}

/**
 * A stream kept beside the subtitles only for its timestamps: the first audio stream, else
 * the first video. A subtitle track alone cannot say how far the demuxer has read — a track
 * silent for minutes reads on to its next line, which on a sparse track is the end of the
 * film. Its packets are dropped as they arrive; the clusters holding them are read anyway.
 */
private fun AVFormatContext.clockStream(subtitle: Int): Int {
    val types = (0 until nb_streams()).associateWith { streams(it)?.codecpar()?.codec_type() }
    return types.entries.firstOrNull { it.key != subtitle && it.value == AVMEDIA_TYPE_AUDIO }?.key
        ?: types.entries.firstOrNull { it.key != subtitle && it.value == AVMEDIA_TYPE_VIDEO }?.key
        ?: -1
}

/** An opened decoder for this stream, or null when it is not a decodable subtitle. */
internal fun AVStream.openSubtitleCodec(): AVCodecContext? {
    val par = codecpar() ?: return null
    if (par.codec_type() != AVMEDIA_TYPE_SUBTITLE) return null
    val codec = avcodec_find_decoder(par.codec_id()) ?: return null
    val ctx = avcodec_alloc_context3(codec) ?: return null
    if (avcodec_parameters_to_context(ctx, par) >= 0 &&
        avcodec_open2(ctx, codec, null as AVDictionary?) >= 0
    ) {
        return ctx
    }
    avcodec_free_context(ctx)
    return null
}

internal fun AVFormatContext.originMs(): Long = originUs / 1000L

/**
 * Moves the demuxer to at or before [containerMs]; false when the container cannot seek,
 * in which case it is put back at its start and the caller reads forward from there.
 *
 * **The discarded streams are taken back for the seek.** Matroska's index is the video's,
 * and with the video discarded the demuxer found the spot by reading towards it: one hour
 * into a 63.6 GB film took 14.6 s against 5 ms with nothing discarded (measured 2026-11-20,
 * `SubtitleWindowProbe`). Reading after the seek discards them again.
 */
internal fun AVFormatContext.seekBackTo(containerMs: Long): Boolean {
    val discarded = (0 until nb_streams()).filter { streams(it)?.discard() == AVDISCARD_ALL }
    discarded.forEach { streams(it)?.discard(AVDISCARD_DEFAULT) }
    try {
        if (av_seek_frame(this, -1, containerMs * 1000L, AVSEEK_FLAG_BACKWARD) >= 0) return true
        av_seek_frame(this, -1, Long.MIN_VALUE, AVSEEK_FLAG_BACKWARD)
        return false
    } finally {
        discarded.forEach { streams(it)?.discard(AVDISCARD_ALL) }
    }
}

/** This packet's time on the player's clock, or null when it carries none. */
internal fun AVPacket.playerMs(stream: AVStream, originMs: Long): Long? {
    val at = if (pts() != AV_NOPTS_VALUE) pts() else dts()
    val tb = stream.time_base()
    if (at == AV_NOPTS_VALUE || tb.den() == 0) return null
    return at * tb.num() * 1000L / tb.den() - originMs
}

/** The stretches of a track already read, on the player's clock, kept merged. */
internal class ReadSpans {
    private val spans = mutableListOf<LongRange>()

    fun find(atMs: Long): LongRange? = spans.firstOrNull { atMs in it }

    fun add(fromMs: Long, toMs: Long) {
        var merged = fromMs..toMs
        val touching = spans.filter { it.first <= merged.last && merged.first <= it.last }
        touching.forEach { merged = minOf(merged.first, it.first)..maxOf(merged.last, it.last) }
        spans.removeAll(touching)
        spans += merged
    }
}
