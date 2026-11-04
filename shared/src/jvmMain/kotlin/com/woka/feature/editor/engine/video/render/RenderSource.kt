package com.woka.feature.editor.engine.video.render

import com.woka.feature.viewer.video.engine.probe.HdrInfo
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

internal fun probeSource(source: File): SourceProbe = withGrabber(source) { grabber ->
    SourceProbe(grabber.lengthInTime / 1000L, keyframesMs(grabber), grabber.format.orEmpty(), grabber.readStreamHdrInfo())
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
    val grabber = FFmpegFrameGrabber(file)
    try {
        grabber.start()
        return block(grabber)
    } finally {
        runCatching { grabber.stop() }
        runCatching { grabber.release() }
    }
}
