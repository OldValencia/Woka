package com.woka.probe

import org.bytedeco.ffmpeg.global.avcodec.AV_PKT_FLAG_KEY
import org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.ffmpeg.global.avutil.av_make_q
import org.bytedeco.ffmpeg.global.avutil.av_rescale_q
import java.io.File

/** A copied file opened again with a fresh grabber: what a player would make of it. */
internal data class Readback(
    val durationMs: Long = -1,
    val startMs: Long = -1,
    val frames: Int = 0,
    val firstDecodes: Boolean = false,
    val firstFrameMs: Long = -1,
    val lastFrameMs: Long = -1,
    val rotation: Double = 0.0,
    val failure: String? = null
) {
    override fun toString(): String = failure ?: (
        "dur=${durationMs}ms start=${startMs}ms frames=$frames first=${if (firstDecodes) "ok" else "NO"}" +
            " firstTs=${firstFrameMs}ms lastTs=${lastFrameMs}ms rot=${rotation.toInt()}"
        )
}

/**
 * Decodes every video frame of [file]. `start` is the container's `start_time`; the
 * frame stamps are the grabber's, which subtracts nothing — so an output that kept the
 * source's clock shows it here as a first frame far from zero.
 */
internal fun readBack(file: File): Readback = runCatching {
    withGrabber(file) { grabber ->
        val first = grabber.grabImage()
        val firstMs = grabber.timestamp / 1000
        var frames = if (first != null) 1 else 0
        var lastMs = firstMs
        while (grabber.grabImage() != null) {
            frames++
            lastMs = grabber.timestamp / 1000
        }
        Readback(
            durationMs = grabber.lengthInTime / 1000,
            startMs = startOf(grabber) / 1000,
            frames = frames,
            firstDecodes = first != null,
            firstFrameMs = firstMs,
            lastFrameMs = lastMs,
            rotation = grabber.displayRotation
        )
    }
}.getOrElse { Readback(failure = "FAILED ${it.brief()}") }

/** One pass over the packets with nothing decoded. */
internal data class KeyScan(
    val packets: Int = 0,
    val videoPackets: Int = 0,
    val keyframes: Int = 0,
    val noPts: Int = 0,
    val reordered: Int = 0,
    val videoBytes: Long = 0,
    val keyTimesMs: List<Long> = emptyList(),
    val ms: Long = 0,
    val failure: String? = null
) {
    override fun toString(): String = failure ?: (
        "packets=$packets video=$videoPackets keys=$keyframes noPts=$noPts ptsNotDts=$reordered" +
            " scan=${ms}ms keysAt=${keyTimesMs.take(KEYS_SHOWN)}${if (keyTimesMs.size > KEYS_SHOWN) "…" else ""}"
        )
}

private const val KEYS_SHOWN = 8

/**
 * `grabPacket()` to the end, reading `AVPacket.flags & AV_PKT_FLAG_KEY` and `pts` on the
 * main video stream. The timer covers open, scan and close — what a keyframe list for
 * the timeline would cost the first time a file is opened.
 */
internal fun scanKeys(file: File): KeyScan = runCatching {
    val began = System.nanoTime()
    val scan = withGrabber(file) { grabber ->
        val context = grabber.formatContext
        val video = grabber.videoStream
        val millis = av_make_q(1, 1_000)
        var packets = 0
        var videoPackets = 0
        var noPts = 0
        var reordered = 0
        var bytes = 0L
        val keys = ArrayList<Long>()
        while (true) {
            val packet = grabber.grabPacket() ?: break
            packets++
            val index = packet.stream_index()
            if (index != video) continue
            if (context.streams(index).codecpar().codec_type() != AVMEDIA_TYPE_VIDEO) continue
            videoPackets++
            bytes += packet.size()
            if (packet.pts() == AV_NOPTS_VALUE) noPts++
            if (packet.pts() != packet.dts()) reordered++
            if (packet.flags() and AV_PKT_FLAG_KEY != 0) {
                keys += av_rescale_q(packet.pts(), context.streams(index).time_base(), millis)
            }
        }
        KeyScan(packets, videoPackets, keys.size, noPts, reordered, bytes, keys)
    }
    scan.copy(ms = (System.nanoTime() - began) / 1_000_000)
}.getOrElse { KeyScan(failure = "FAILED ${it.brief()}") }
