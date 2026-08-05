package com.woka.feature.viewer.video.engine.subtitle

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avcodec.AVSubtitle
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.global.avcodec.av_new_packet
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_decode_subtitle2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avsubtitle_free
import org.bytedeco.ffmpeg.global.avformat.av_read_frame
import org.bytedeco.ffmpeg.global.avformat.avformat_close_input
import org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info
import org.bytedeco.ffmpeg.global.avformat.avformat_open_input
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_SUBTITLE
import org.bytedeco.javacpp.IntPointer
import com.woka.feature.viewer.video.model.SubtitleImage
import com.woka.feature.viewer.video.model.SubtitleImageRect

/**
 * One bitmap subtitle track (PGS / VobSub), indexed once and expanded on demand
 * (Step 10 / §2.2).
 *
 * The memory model is the whole point: the index keeps compressed packets and the
 * pictures are decoded only when the play head reaches them — decoding a display
 * set costs a millisecond or two, while keeping every decoded cue of a film alive
 * costs gigabytes. The codec context stays open for the session because that is
 * what re-feeding a single event needs.
 */
internal class BitmapSubtitleSource(
    private val cues: List<BitmapCue>,
    private val ctx: AVCodecContext,
    private val canvasWidth: Int,
    private val canvasHeight: Int
) {
    val cueCount: Int get() = cues.size
    val retainedBytes: Int get() = cues.sumOf { it.retainedBytes }

    fun cueAt(positionMs: Long): BitmapCue? =
        cues.firstOrNull { positionMs in it.startMs..it.endMs }

    fun decode(cue: BitmapCue): SubtitleImage? {
        val sub = AVSubtitle()
        val gotSub = IntPointer(1)
        val pkt = AVPacket()
        var rects: List<SubtitleImageRect> = emptyList()
        for (payload in cue.payload) {
            if (av_new_packet(pkt, payload.size) < 0) continue
            pkt.data().capacity(payload.size.toLong()).put(payload, 0, payload.size)
            val decoded = avcodec_decode_subtitle2(ctx, sub, gotSub, pkt)
            av_packet_unref(pkt)
            if (decoded < 0 || gotSub.get() == 0) continue
            if (sub.num_rects() > 0) {
                rects = sub.toImageRects()
            }
            avsubtitle_free(sub)
        }
        return if (rects.isEmpty()) null else SubtitleImage(canvasWidth, canvasHeight, rects)
    }

    fun close() {
        avcodec_free_context(ctx)
    }
}

/**
 * Opens a bitmap subtitle sidecar: a raw `.sup` through FFmpeg's `sup` demuxer, or
 * a VobSub pair through the `vobsub` demuxer pointed at the **`.idx`** — which
 * finds the `.sub` beside it and loads the palette itself. That is why the pair
 * needs no special case in the one-path-per-entry model (Step 9).
 */
internal fun openBitmapSubtitleFile(path: String): BitmapSubtitleSource? {
    val startedAt = System.currentTimeMillis()
    val fc = AVFormatContext(null)
    if (avformat_open_input(fc, path, null, null) != 0) return null
    if (avformat_find_stream_info(fc, null as AVDictionary?) < 0) {
        avformat_close_input(fc)
        return null
    }
    var source: BitmapSubtitleSource? = null
    try {
        val index = (0 until fc.nb_streams()).firstOrNull {
            fc.streams(it)?.codecpar()?.codec_type() == AVMEDIA_TYPE_SUBTITLE
        }
        val stream = index?.let { fc.streams(it) }
        val ctx = stream?.openSubtitleCodec()
        if (index != null && stream != null && ctx != null) {
            val indexer = BitmapTrackIndexer(ctx, stream)
            val pkt = AVPacket()
            val sub = AVSubtitle()
            val gotSub = IntPointer(1)
            while (av_read_frame(fc, pkt) >= 0) {
                if (pkt.stream_index() == index) {
                    indexer.feed(pkt, sub, gotSub)
                }
                av_packet_unref(pkt)
            }
            val par = stream.codecpar()
            source = BitmapSubtitleSource(indexer.finish(), ctx, par.width(), par.height())
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        avformat_close_input(fc)
    }
    source?.let { logBitmapSubtitles(mapOf(0 to it), System.currentTimeMillis() - startedAt) }
    return source
}
