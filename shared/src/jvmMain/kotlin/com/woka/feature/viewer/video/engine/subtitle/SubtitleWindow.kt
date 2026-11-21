package com.woka.feature.viewer.video.engine.subtitle

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avcodec.AVSubtitle
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.avformat.AVStream
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.avcodec_flush_buffers
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avformat.av_read_frame
import org.bytedeco.ffmpeg.global.avformat.avformat_close_input
import org.bytedeco.javacpp.IntPointer
import com.woka.feature.viewer.video.model.Cue
import com.woka.feature.viewer.video.model.SubtitleImage

/** Read behind the play head, so a line that began just before a seek is still found. */
private const val BEHIND_MS = 10_000L

/** How far past the play head one read reaches. */
private const val AHEAD_MS = 40_000L

/**
 * The first read after a seek: enough to find the line showing now, 23–58 ms against 190–380
 * for the whole window on a 4K film (`SubtitleWindowProbe`); the rest follows next tick.
 */
private const val FIRST_AHEAD_MS = 3_000L

/** Read on once the play head is this close to the end of what has been read. */
private const val REFILL_MS = 20_000L

/** How far behind the stopping packet a line may still be muxed; what was read ends here. */
private const val INTERLEAVE_MS = 2_000L

/**
 * One embedded subtitle track, read **around the play head** rather than in a pass over the
 * whole container (reported 2026-11-20). Matroska keeps subtitles in the same clusters as
 * the video, so reading every subtitle packet of a film reads every byte of it: 32.7 s for a
 * 63.6 GB `mkv`, with nothing shown until it ended. Here a seek reads [BEHIND_MS] before the
 * play head to [AHEAD_MS] after it, playback reads on from where the demuxer stands, and what
 * was read is kept — going back costs nothing.
 *
 * Bitmap tracks index with one decoder and draw with another ([drawCtx]): a window can stop
 * between the segments of a PGS display set, and drawing through the indexing decoder there
 * would corrupt the set it is halfway through. Used from the subtitle loop's thread only.
 */
internal class SubtitleWindow(
    private val fc: AVFormatContext,
    private val index: Int,
    /** A stream read only for its timestamps (`clockStream`), or -1 for none. */
    private val clock: Int,
    private val stream: AVStream,
    private val indexCtx: AVCodecContext,
    private val drawCtx: AVCodecContext?
) {
    val isBitmap: Boolean get() = drawCtx != null
    private val originMs = fc.originMs()
    private val cues = WindowCues(originMs)
    private val read = ReadSpans()
    private var indexer = drawCtx?.let { BitmapTrackIndexer(indexCtx, stream) }

    /** Where the demuxer stands, on the player's clock; null once it reached the end. */
    private var frontierMs: Long? = null
    private var spanStartMs = 0L
    private val pkt = AVPacket()
    private val sub = AVSubtitle()
    private val gotSub = IntPointer(1)

    val cueCount: Int get() = cues.count
    val retainedBytes: Int get() = cues.retainedBytes

    /** Everything read so far, in order — for tests; the player asks [textAt]. */
    val textCues: List<Cue> get() = cues.textCues

    fun textAt(positionMs: Long): Cue? {
        cover(positionMs)
        return cues.textAt(positionMs)
    }

    fun bitmapAt(positionMs: Long): BitmapCue? {
        cover(positionMs)
        return cues.bitmapAt(positionMs, indexer?.openCue())
    }

    fun decode(cue: BitmapCue): SubtitleImage? = drawCtx?.decodeBitmapCue(
        cue,
        stream.codecpar().width(),
        stream.codecpar().height()
    )

    /** Whether [positionMs] has been read — for tests that prove a read stopped short. */
    fun hasRead(positionMs: Long): Boolean = read.find(positionMs) != null

    fun close() {
        drawCtx?.let { avcodec_free_context(it) }
        avcodec_free_context(indexCtx)
        avformat_close_input(fc)
    }

    private fun cover(positionMs: Long) {
        val span = read.find(positionMs)
        if (span != null && (span.last == Long.MAX_VALUE || span.last - positionMs > REFILL_MS)) return
        if (span == null || frontierMs != span.last) {
            spanStartMs = (positionMs - BEHIND_MS).coerceAtLeast(0L)
            restart(spanStartMs)
            return readUntil(positionMs + FIRST_AHEAD_MS)
        }
        readUntil(positionMs + AHEAD_MS)
    }

    /** A seek: the demuxer moves, and a bitmap index starts a new run of events. */
    private fun restart(fromMs: Long) {
        if (!fc.seekBackTo(fromMs + originMs)) spanStartMs = 0L
        avcodec_flush_buffers(indexCtx)
        indexer?.let { cues.addBitmap(it.finish()) }
        indexer = drawCtx?.let { BitmapTrackIndexer(indexCtx, stream) }
    }

    private fun readUntil(untilMs: Long) {
        while (true) {
            if (av_read_frame(fc, pkt) < 0) {
                frontierMs = null
                read.add(spanStartMs, Long.MAX_VALUE)
                indexer?.let { cues.addBitmap(it.finish()) }
                return
            }
            val from = pkt.stream_index()
            val atMs = if (from == index || from == clock) pkt.playerMs(fc.streams(from), originMs) else null
            if (from == index) feed()
            av_packet_unref(pkt)
            if (atMs != null && atMs > untilMs) {
                // Short of the packet that stopped it: a line muxed a moment behind that
                // packet is still ahead in the file, and the next read must not skip it.
                val reachedMs = atMs - INTERLEAVE_MS
                frontierMs = reachedMs
                read.add(spanStartMs, reachedMs)
                indexer?.let { cues.addBitmap(it.takeClosed()) }
                return
            }
        }
    }

    private fun feed() {
        val bitmapIndexer = indexer
        if (bitmapIndexer != null) return bitmapIndexer.feed(pkt, sub, gotSub)
        val found = mutableListOf<Cue>()
        SubtitleParser.decodePacket(indexCtx, stream, pkt, sub, gotSub, found)
        cues.addText(found)
    }
}
