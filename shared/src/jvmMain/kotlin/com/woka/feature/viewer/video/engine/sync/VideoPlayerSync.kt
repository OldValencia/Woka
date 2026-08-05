package com.woka.feature.viewer.video.engine.sync

import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.track.TrackChoice
import com.woka.feature.viewer.video.engine.track.persistTrackPrefs

/**
 * Subtitle / audio sync offset (§3). A release whose subtitles or dub were muxed
 * from a different source drifts against the picture; this shifts the *lookup*,
 * so no stream is ever re-decoded and seek, pause and repeat keep working.
 */

/** One click of a stepper. */
const val SYNC_STEP_MS = 50L

/** How far either stream may be shifted in each direction. */
const val SYNC_LIMIT_MS = 30_000L

/** The audio timestamp that lines up with video position [videoMs]. */
internal fun VideoPlayerState.audioTimeFor(videoMs: Long): Long =
    (videoMs - audioDelayMs).coerceAtLeast(0L)

/** The position cues are looked up at, under the current subtitle offset. */
internal val VideoPlayerState.subtitleTimeMs: Long
    get() = currentPositionMs - subtitleDelayMs

fun VideoPlayerState.setSubtitleDelay(delayMs: Long) {
    val clamped = delayMs.coerceIn(-SYNC_LIMIT_MS, SYNC_LIMIT_MS)
    if (clamped == subtitleDelayMs) return
    subtitleDelayMs = clamped
    tracks.markChosen(TrackChoice.Sync)
    persistTrackPrefs()
}

fun VideoPlayerState.setAudioDelay(delayMs: Long) {
    val clamped = delayMs.coerceIn(-SYNC_LIMIT_MS, SYNC_LIMIT_MS)
    if (clamped == audioDelayMs) return
    audioDelayMs = clamped
    generation.requestAudioDelayChange()
    tracks.markChosen(TrackChoice.Sync)
    persistTrackPrefs()
}

/** Returns both streams to the container's own timing. */
fun VideoPlayerState.resetSync() {
    setSubtitleDelay(0L)
    setAudioDelay(0L)
}

/** Signed, human-readable offset for the indicator: `0 ms`, `+250 ms`, `−500 ms`. */
fun formatSyncOffset(delayMs: Long): String = when {
    delayMs > 0L -> "+$delayMs ms"
    delayMs < 0L -> "−${-delayMs} ms"
    else -> "0 ms"
}
