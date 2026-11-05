package com.woka.feature.editor.engine.video.render

import com.woka.core.data.TransferKind
import com.woka.feature.viewer.video.engine.decode.FreshFrameGrabber
import com.woka.feature.viewer.video.engine.decode.lastPicture
import com.woka.feature.viewer.video.engine.probe.HdrInfo
import com.woka.feature.viewer.video.engine.probe.readFrom
import com.woka.feature.viewer.video.engine.sync.lengthMs
import com.woka.feature.viewer.video.engine.track.readStreamHdrInfo
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File

/**
 * What a render needs to know about its source before it picks a path. Split out of
 * `VideoRender` when the stream's colour (HDR in a render) took that file past the size
 * mandate.
 */
internal class SourceProbe(
    val durationMs: Long,
    val keyframesMs: List<Long>,
    val format: String,
    /** The stream's colour — a PQ or HLG source is tone mapped on the encode path. */
    val hdr: HdrInfo
)

internal fun probeSource(source: File, alive: () -> Boolean = { true }): SourceProbe = withGrabber(source) { grabber ->
    val keyframes = keyframesMs(grabber, alive)
    // The player's length, which counts a file whose header states none (VF-001); the header's
    // 0 rendered a `[0, 0)` film into an empty file reported as saved (1.0.6 review).
    SourceProbe(grabber.lengthMs(source), keyframes, grabber.format.orEmpty(), declaredColour(grabber))
}

/**
 * The stream's colour with the film's declared white point (ME-301), read off its first
 * picture — one decode, on an HDR source only, and after the packet walk, which has to see
 * the first keyframe itself.
 */
private fun declaredColour(grabber: FFmpegFrameGrabber): HdrInfo {
    val hdr = grabber.readStreamHdrInfo()
    if (hdr.transfer == TransferKind.SDR) return hdr
    runCatching {
        grabber.setTimestamp(0L)
        grabber.grabImage()
    }
    return hdr.readFrom(grabber.lastPicture)
}

/**
 * The muxer for [extension]. Named explicitly where it matters, because the grabber's own
 * format is a list — `mov,mp4,m4a,3gp,3g2,mj2` — and its first entry is not always the
 * file's (ME-290).
 */
internal fun containerFormat(extension: String, probed: String): String = when (extension.lowercase()) {
    "mp4", "m4v" -> "mp4"
    "mov" -> "mov"
    "mkv" -> "matroska"
    "webm" -> "webm"
    "avi" -> "avi"
    else -> probed.substringBefore(',').ifEmpty { "mp4" }
}

internal fun <T> withGrabber(file: File, block: (FFmpegFrameGrabber) -> T): T {
    val grabber = FreshFrameGrabber(file)
    try {
        grabber.start()
        return block(grabber)
    } finally {
        runCatching { grabber.stop() }
        runCatching { grabber.release() }
    }
}
