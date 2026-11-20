package com.woka.support

import com.woka.feature.viewer.video.engine.probe.probeMediaTracks
import com.woka.feature.viewer.video.engine.subtitle.BitmapSubtitleSource
import com.woka.feature.viewer.video.engine.subtitle.SubtitleWindow
import com.woka.feature.viewer.video.engine.subtitle.openSubtitleWindow
import com.woka.feature.viewer.video.model.MediaTrack
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File

/**
 * A started grabber, always released. [videoStream] selects one of a multi-video
 * container's streams by its **absolute** container index, the same number
 * `MediaTrack.index` carries.
 */
fun <T> onGrabber(file: File, videoStream: Int = -1, block: (FFmpegFrameGrabber) -> T): T {
    val grabber = FFmpegFrameGrabber(file)
    if (videoStream >= 0) grabber.videoStream = videoStream
    grabber.start()
    return try {
        block(grabber)
    } finally {
        runCatching { grabber.stop() }
        runCatching { grabber.release() }
    }
}

fun subtitleTracks(file: File): List<MediaTrack> = onGrabber(file) { probeMediaTracks(it).subtitle }

/**
 * A window on every embedded subtitle track of [file], keyed by stream index and closed
 * afterwards — a window holds a demuxer and decoders open until it is.
 */
internal fun <T> onSubtitleWindows(file: File, block: (Map<Int, SubtitleWindow>) -> T): T {
    val windows = subtitleTracks(file).mapNotNull { track ->
        openSubtitleWindow(file.absolutePath, track.index, bitmap = track.isTextBased == false)?.let { track.index to it }
    }.toMap()
    return try {
        block(windows)
    } finally {
        windows.values.forEach { it.close() }
    }
}

/** The play head walked through [untilMs] a second at a time, as playback would read it. */
internal fun SubtitleWindow.playThrough(untilMs: Long = 15 * 60_000L) {
    var at = 0L
    while (at <= untilMs) {
        if (isBitmap) bitmapAt(at) else textAt(at)
        at += 1_000L
    }
}

/** The start of the earliest cue, walked for rather than exposing the cue list. */
internal fun firstCueStart(source: BitmapSubtitleSource): Long = firstCueStart { source.cueAt(it)?.startMs }

internal fun firstCueStart(window: SubtitleWindow): Long = firstCueStart { window.bitmapAt(it)?.startMs }

private fun firstCueStart(startAt: (Long) -> Long?): Long {
    var at = 0L
    while (at < 15 * 60_000L) {
        startAt(at)?.let { return it }
        at += 20L
    }
    throw AssertionError("no cue found in the first 15 minutes of the track")
}
