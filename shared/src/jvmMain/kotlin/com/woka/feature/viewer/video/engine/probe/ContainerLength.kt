package com.woka.feature.viewer.video.engine.probe

import com.woka.feature.viewer.image.onFormatContext
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import java.io.File

/** The demuxer whose declared length is its wall clock, not its film (`MediaTimeline.lengthMs`). */
private const val DAHUA_DEMUXER = "dhav"

/**
 * A film's length in ms without a grabber or a decoder — for the carousel's badge (RD-502),
 * which asks it of every film it shows.
 *
 * **The rule `lengthMs` applies, on a bare format context**: the container's own length;
 * where it states none, or is `dhav`, the one [measuredDurationMs] counts. The header is read
 * first, with no `avformat_find_stream_info` — enough for MKV and MP4. A container whose
 * header carries no length gets the stream-info pass a grabber always makes: measured
 * 2026-11-28, `blank.bik` declares nothing until it (the grabber's 40 ms, the header's 0).
 * 0 when nothing is known, or the file does not open.
 */
internal fun containerLengthMs(file: File): Long {
    val header = declared(file, findStreamInfo = false) ?: return 0L
    val (ms, demuxer) = if (header.first > 0) header else declared(file, findStreamInfo = true) ?: header
    if (ms > 0 && demuxer != DAHUA_DEMUXER) return ms
    return measuredDurationMs(file) ?: ms.coerceAtLeast(0L)
}

/** The container's stated length in ms and its demuxer's name; null when it does not open. */
private fun declared(file: File, findStreamInfo: Boolean): Pair<Long, String>? = runCatching {
    onFormatContext(file, findStreamInfo) { format ->
        val us = format.duration().takeIf { it != AV_NOPTS_VALUE } ?: 0L
        (us / 1000L) to format.iformat().name().string
    }
}.getOrNull()
