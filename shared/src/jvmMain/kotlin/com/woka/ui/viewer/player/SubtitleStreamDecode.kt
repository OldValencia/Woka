package com.woka.ui.viewer.player

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avcodec.AVSubtitle
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avformat.AVStream
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.global.avcodec.AVDISCARD_ALL
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_to_context
import org.bytedeco.ffmpeg.global.avformat.av_read_frame
import org.bytedeco.ffmpeg.global.avformat.avformat_close_input
import org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info
import org.bytedeco.ffmpeg.global.avformat.avformat_open_input
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_SUBTITLE
import org.bytedeco.javacpp.IntPointer

/** What one pass over the container yields: parsed text cues and indexed bitmap tracks. */
internal class SubtitleStreams(
    val text: Map<Int, List<Cue>>,
    val bitmap: Map<Int, BitmapSubtitleSource>
) {
    fun close() = bitmap.values.forEach { it.close() }

    companion object {
        val EMPTY = SubtitleStreams(emptyMap(), emptyMap())
    }
}

/**
 * Reads every embedded subtitle track in one pass (§2.2): text tracks are parsed
 * into [Cue] lists up front, bitmap tracks are indexed (timings + compressed
 * packets, no pictures — see [BitmapSubtitleSource]).
 *
 * Bitmap tracks are indexed here rather than when one is first picked because this
 * walk crosses the whole container — a slow thing to hang off a menu click on a
 * 20 GB file, and free here since the walk happens on open regardless.
 */
internal fun decodeEmbeddedSubtitles(
    filepath: String,
    textIndices: Set<Int>,
    bitmapIndices: Set<Int>
): SubtitleStreams {
    val wanted = textIndices + bitmapIndices
    if (wanted.isEmpty()) return SubtitleStreams.EMPTY
    val startedAt = System.currentTimeMillis()
    val fc = AVFormatContext(null)
    if (avformat_open_input(fc, filepath, null, null) != 0) return SubtitleStreams.EMPTY
    if (avformat_find_stream_info(fc, null as AVDictionary?) < 0) {
        avformat_close_input(fc)
        return SubtitleStreams.EMPTY
    }
    // Every other stream is read only to be thrown away, so the demuxer is told to
    // drop them instead of handing over 4K video packet by packet.
    for (i in 0 until fc.nb_streams()) {
        if (i !in wanted) fc.streams(i)?.discard(AVDISCARD_ALL)
    }

    val contexts = openCodecContexts(fc, wanted)
    val indexers = bitmapIndices.mapNotNull { index ->
        val stream = fc.streams(index) ?: return@mapNotNull null
        val ctx = contexts[index] ?: return@mapNotNull null
        index to BitmapTrackIndexer(ctx, stream)
    }.toMap()
    val textCues = mutableMapOf<Int, MutableList<Cue>>()

    val pkt = AVPacket()
    val sub = AVSubtitle()
    val gotSub = IntPointer(1)
    try {
        while (av_read_frame(fc, pkt) >= 0) {
            val index = pkt.stream_index()
            val ctx = contexts[index]
            val indexer = indexers[index]
            when {
                indexer != null -> indexer.feed(pkt, sub, gotSub)
                ctx != null -> SubtitleParser.decodePacket(
                    ctx, fc.streams(index), pkt, sub, gotSub,
                    textCues.getOrPut(index) { mutableListOf() }
                )
            }
            av_packet_unref(pkt)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // A bitmap source keeps its codec context alive for on-demand decoding; the
    // text ones are done the moment the pass ends.
    val bitmapSources = indexers.mapNotNull { (index, indexer) ->
        val ctx = contexts[index] ?: return@mapNotNull null
        val par = fc.streams(index)?.codecpar()
        index to BitmapSubtitleSource(indexer.finish(), ctx, par?.width() ?: 0, par?.height() ?: 0)
    }.toMap()
    contexts.filterKeys { it !in bitmapSources }.values.forEach { avcodec_free_context(it) }
    avformat_close_input(fc)
    logBitmapSubtitles(bitmapSources, System.currentTimeMillis() - startedAt)
    return SubtitleStreams(textCues, bitmapSources)
}

/**
 * The retained size is the number the whole memory model rests on, so it is
 * reported rather than assumed — same spirit as `logMediaTracks`.
 */
internal fun logBitmapSubtitles(sources: Map<Int, BitmapSubtitleSource>, elapsedMs: Long) {
    sources.forEach { (index, source) ->
        println(
            "[BitmapSubs] stream #$index: ${source.cueCount} cues, " +
                "${source.retainedBytes / 1024} KB retained, indexed in $elapsedMs ms"
        )
    }
}

private fun openCodecContexts(fc: AVFormatContext, indices: Set<Int>): Map<Int, AVCodecContext> {
    val map = mutableMapOf<Int, AVCodecContext>()
    for (index in indices) {
        val stream = fc.streams(index) ?: continue
        stream.openSubtitleCodec()?.let { map[index] = it }
    }
    return map
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
