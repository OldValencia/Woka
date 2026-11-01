package com.woka.feature.viewer.video.engine.sync

import com.woka.feature.viewer.video.engine.probe.measuredDurationMs
import org.bytedeco.ffmpeg.avformat.AVFormatContext
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.io.File

/**
 * `dhav` states a length of its own, read from the file's tail, and it is wrong on
 * a cut export: 1000 ms on `dahua.dav`, whose packets run 1939 ms (VF-001, probe
 * 2026-11-01). Its length is always counted.
 */
private const val DHAV = "dhav"

/**
 * Where the container's clock starts, in µs. **Every time the player shows is
 * measured from here** (VF-001, 2026-11-01): JavaCV's `timestamp` is the raw
 * presentation time while its `setTimestamp` adds `start_time` itself, so the two
 * only agree on a file that starts at zero. A Dahua `.dav` starts at its wall-clock
 * time — 1455778979 s — and a generated `.ts` at 1.44 s, where every seek used to
 * overshoot, restart the file and land 1.44 s before its target.
 */
internal val AVFormatContext.originUs: Long
    get() = start_time().takeIf { it != AV_NOPTS_VALUE } ?: 0L

/** The last decoded frame's time on the player's timeline — see [originUs]. */
internal val FFmpegFrameGrabber.mediaTimeUs: Long
    get() = timestamp - (formatContext?.originUs ?: 0L)

/**
 * The length the app shows, in ms: the container's own, or — when it states none,
 * or is [DHAV] — the one [measuredDurationMs] counts. 0 when neither is known.
 */
internal fun FFmpegFrameGrabber.lengthMs(file: File): Long {
    val declared = lengthInTime / 1000L
    val demuxer = formatContext?.iformat()?.name()?.string
    if (declared > 0 && demuxer != DHAV) return declared
    return measuredDurationMs(file) ?: declared.coerceAtLeast(0L)
}

/**
 * The first picture at or after [targetUs] on the media timeline, for the paths
 * that only need one: the poster, the hover reel, the seek preview.
 *
 * **A container that cannot seek is decoded forward from the start instead.**
 * Measured 2026-11-01 (VF-001): `setTimestamp` throws on `.thp`, `.rpl`, `.mtv`
 * and `.nsv` and leaves the grabber at EOF. A seek that succeeds and is followed by
 * EOF looks the same from here — no picture after it — so the fallback covers that
 * too. Null when the stream ends before [targetUs].
 */
internal fun FFmpegFrameGrabber.grabImageAt(targetUs: Long): Frame? {
    val seeked = runCatching { setTimestamp(targetUs) }.isSuccess
    if (seeked) decodeForwardTo(targetUs)?.let { return it }
    restart()
    return decodeForwardTo(targetUs)
}

/**
 * Walks on from wherever the grabber stands. Also what makes a long `.amv` exact:
 * its demuxer seeks back to the start, and `setTimestamp` walks forward 500 frames
 * at most before returning — 20 s of a 450 s target at 25 fps, 41.7 s of 64.5 s
 * at 12 fps (2026-11-01). The picture is right for where it stopped, so the walk
 * ends on the target.
 */
private fun FFmpegFrameGrabber.decodeForwardTo(targetUs: Long): Frame? {
    while (true) {
        val frame = grabImage() ?: return null
        if (frame.image != null && mediaTimeUs >= targetUs) return frame
    }
}
