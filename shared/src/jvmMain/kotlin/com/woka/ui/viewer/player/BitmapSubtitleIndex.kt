package com.woka.ui.viewer.player

import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avcodec.AVSubtitle
import org.bytedeco.ffmpeg.avformat.AVStream
import org.bytedeco.ffmpeg.global.avcodec.avcodec_decode_subtitle2
import org.bytedeco.ffmpeg.global.avcodec.avsubtitle_free
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.javacpp.IntPointer

/** How long a cue with nothing to close it is left on screen. */
private const val TRAILING_CUE_MS = 5000L

/**
 * Ceiling on an end the decoder only *implies* — one taken from where the next cue
 * begins rather than from a closing event. Without it a sparse track parks a
 * subtitle on screen for minutes: the fixture `.idx` has a four-minute gap.
 */
private const val MAX_GUESSED_CUE_MS = 10_000L

/**
 * A declared end longer than this is not a duration, it is a decoder saying it
 * found none. VobSub without a stop-display command reports the DVD maximum —
 * measured at exactly 745 642 ms (65 535 date ticks) on every packet of the
 * `.idx` fixture, while the same subtitles muxed into MKV declare a real 4 994 ms.
 */
private const val MAX_DECLARED_CUE_MS = 30_000L

/**
 * One bitmap subtitle event: its timing plus the **compressed** packets that
 * produce it (Step 10). The pixels are not kept — a 1920×200 rectangle is 1.5 MB
 * as RGBA, so a film's worth of them is gigabytes, while the RLE payload behind
 * the same cue is a few kilobytes.
 */
internal data class BitmapCue(
    val startMs: Long,
    val endMs: Long,
    val payload: List<ByteArray>
) {
    val retainedBytes: Int get() = payload.sumOf { it.size }
}

/**
 * Builds the cue index of one bitmap subtitle track from the packets of a single
 * container pass, decoding each event only to learn its timing and whether it
 * carries a picture at all.
 *
 * Two shapes have to survive this: an `.idx`/mkv event is one packet, while a raw
 * `.sup` emits **one packet per PGS segment** — five of them for a single cue, at
 * the same timestamp — so packets accumulate until the decoder actually yields an
 * event, and that whole group is what the cue retains.
 */
internal class BitmapTrackIndexer(
    private val ctx: AVCodecContext,
    private val stream: AVStream
) {
    private val pending = mutableListOf<ByteArray>()
    private val cues = mutableListOf<BitmapCue>()
    private var openCue: BitmapCue? = null

    fun feed(pkt: AVPacket, sub: AVSubtitle, gotSub: IntPointer) {
        pkt.copyPayload()?.let { pending += it }
        if (avcodec_decode_subtitle2(ctx, sub, gotSub, pkt) < 0 || gotSub.get() == 0) return
        val startMs = startMsOf(pkt, sub)
        val declaredEnd = declaredEndMsOf(pkt, sub, startMs)
        val hasPicture = sub.num_rects() > 0
        avsubtitle_free(sub)
        // Any event ends whatever was showing. A display set carrying no rectangles
        // is PGS explicitly clearing the screen, so that end is exact; the start of
        // the *next* cue is only an upper bound on where this one stopped.
        closeAt(startMs, exact = !hasPicture)
        if (hasPicture) {
            openCue = BitmapCue(startMs, declaredEnd, pending.toList())
        }
        pending.clear()
    }

    fun finish(): List<BitmapCue> {
        val open = openCue ?: return cues.toList()
        val end = if (open.endMs > open.startMs) open.endMs else open.startMs + TRAILING_CUE_MS
        cues += open.copy(endMs = end)
        openCue = null
        return cues.toList()
    }

    /**
     * The declared end wins when the decoder gave one (dvdsub reads the stop-display
     * command out of the SPU); otherwise the cue runs until [atMs], the next event —
     * capped when that boundary is a guess rather than an explicit clear.
     */
    private fun closeAt(atMs: Long, exact: Boolean) {
        val open = openCue ?: return
        val boundary = if (exact) atMs else minOf(atMs, open.startMs + MAX_GUESSED_CUE_MS)
        val end = if (open.endMs > open.startMs) minOf(open.endMs, boundary) else boundary
        if (end > open.startMs) {
            cues += open.copy(endMs = end)
        }
        openCue = null
    }

    private fun startMsOf(pkt: AVPacket, sub: AVSubtitle): Long =
        baseMsOf(pkt, sub) + sub.start_display_time().toLong()

    private fun declaredEndMsOf(pkt: AVPacket, sub: AVSubtitle, startMs: Long): Long {
        val end = baseMsOf(pkt, sub) + sub.end_display_time().toLong()
        if (end > startMs && end - startMs <= MAX_DECLARED_CUE_MS) return end
        val tb = stream.time_base()
        val duration = pkt.duration()
        if (duration > 0 && tb.den() != 0) {
            return startMs + (duration * tb.num() * 1000L) / tb.den()
        }
        return 0L
    }

    private fun baseMsOf(pkt: AVPacket, sub: AVSubtitle): Long {
        if (sub.pts() != AV_NOPTS_VALUE) return sub.pts() / 1000L
        val tb = stream.time_base()
        if (pkt.pts() == AV_NOPTS_VALUE || tb.den() == 0) return 0L
        return (pkt.pts() * tb.num() * 1000L) / tb.den()
    }
}
