package com.woka.feature.viewer.video.engine

import com.woka.core.data.RepeatMode

// How close to the end counts as "at the end".
private const val END_THRESHOLD_MS = 200L

/**
 * Playback transport for [VideoPlayerState] — seeking and play/pause. Split out
 * of the state class per the §2 file-size mandate, following the package's
 * existing shape: [VideoPlayerState] holds fields, behaviour lives in extension
 * files beside it (see `VideoPlayerGrabber`, `VideoPlayerSeek`).
 */
fun VideoPlayerState.seek(progressMs: Long) {
    val safeProgress = progressMs.coerceIn(0L, durationMs)
    clearReachedEnd()
    seekGeneration++
    seekTargetMs = safeProgress
    currentPositionMs = safeProgress

    // Seeking to (or past) the very end is treated as reaching the end:
    // pin the play head there and pause, instead of relying on the decoder
    // to play through to EOF (which is racy and lets the position bounce
    // back on stale frames). The decoder still seeks and decodes the last
    // frame for display. The next Play then restarts from the beginning
    // (see togglePlayPause).
    if (durationMs > 0 && safeProgress >= durationMs - END_THRESHOLD_MS) {
        currentPositionMs = durationMs
        isPlayingBeforeSeek = false
        isPlaying = false
    }
}

/**
 * Relative skip (the ±5s buttons and Alt+arrows). Under Repeat File a forward
 * skip off the end wraps to the start and keeps playing — pausing there would
 * contradict the mode. Off and Folder keep [seek]'s pin-and-pause: Folder hands
 * over to the playlist layer, which advances the file (§6).
 */
fun VideoPlayerState.skipBy(deltaMs: Long) {
    val target = currentPositionMs + deltaMs
    val pastEnd = durationMs > 0 && target >= durationMs - END_THRESHOLD_MS
    if (deltaMs > 0 && pastEnd && repeatMode == RepeatMode.File) {
        seek(0)
        return
    }
    seek(target.coerceIn(0L, durationMs))
}

fun VideoPlayerState.pauseForSeek() {
    if (!isSeeking) {
        isPlayingBeforeSeek = isPlaying
        isSeeking = true
        isPlaying = false
    }
}

fun VideoPlayerState.resumeAfterSeek() {
    if (isSeeking) {
        isSeeking = false
        if (isPlayingBeforeSeek) {
            isPlaying = true
        }
    }
}

fun VideoPlayerState.togglePlayPause() {
    if (!isPlaying && durationMs > 0 && currentPositionMs >= durationMs - END_THRESHOLD_MS) {
        seek(0)
    }
    clearReachedEnd()
    isPlaying = !isPlaying
}
