package com.woka.support

import com.woka.feature.viewer.video.engine.probe.probeMediaTracks
import com.woka.feature.viewer.video.engine.subtitle.BitmapSubtitleSource
import com.woka.feature.viewer.video.engine.subtitle.SubtitleStreams
import com.woka.feature.viewer.video.engine.subtitle.decodeEmbeddedSubtitles
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
 * One demux pass over [file], closed afterwards — a bitmap source keeps its codec
 * context open for on-demand decoding, so it has to be released explicitly.
 */
internal fun <T> onSubtitleStreams(file: File, block: (SubtitleStreams) -> T): T {
    val tracks = subtitleTracks(file)
    val streams = decodeEmbeddedSubtitles(
        filepath = file.absolutePath,
        textIndices = tracks.filter { it.isTextBased == true }.map { it.index }.toSet(),
        bitmapIndices = tracks.filter { it.isTextBased == false }.map { it.index }.toSet()
    )
    return try {
        block(streams)
    } finally {
        streams.close()
    }
}

/** The start of the earliest cue, walked for rather than exposing the cue list. */
internal fun firstCueStart(source: BitmapSubtitleSource): Long {
    var at = 0L
    while (at < 15 * 60_000L) {
        source.cueAt(at)?.let { return it.startMs }
        at += 20L
    }
    throw AssertionError("no cue found in the first 15 minutes of the track")
}
