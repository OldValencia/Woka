package com.woka.feature.viewer.video.engine

import com.woka.core.data.RepeatMode
import com.woka.feature.viewer.video.model.Cue

// How close to the end counts as "at the end".
private const val END_THRESHOLD_MS = 200L

/** Where unmuting lands when nothing audible was ever heard in this file. */
const val DEFAULT_UNMUTE_VOLUME = 0.5f

/**
 * Mute ↔ the level you were at.
 *
 * Takes the level and a setter rather than driving [VideoPlayerState.volume]
 * directly, because the two windows own the volume differently: the main one
 * writes the persisted `mediaVolume` through a callback that also flashes the HUD,
 * the fullscreen window writes the player's own field. The remembered level lives
 * on the player either way, so muting with the speaker button and unmuting with M
 * agree instead of each having its own idea of where to return to.
 */
fun VideoPlayerState.toggleMute(current: Float, apply: (Float) -> Unit) {
    if (current > 0f) {
        volumeBeforeMute = current
        apply(0f)
    } else {
        apply(volumeBeforeMute.takeIf { it > 0f } ?: DEFAULT_UNMUTE_VOLUME)
    }
}

/**
 * The cue that should be on screen right now, if any. Moved off the state class to
 * make room there; the sibling `hasSubtitle` went the other way and was dropped —
 * `tracks.hasAnySubtitle` says the same thing without a forwarding hop.
 */
val VideoPlayerState.activeCue: Cue? get() = tracks.activeCue

/**
 * Playback transport for [VideoPlayerState] — seeking and play/pause. Split out
 * of the state class per the §2 file-size mandate, following the package's
 * existing shape: [VideoPlayerState] holds fields, behaviour lives in extension
 * files beside it (see `VideoPlayerGrabber`, `VideoPlayerSeek`).
 */
fun VideoPlayerState.seek(progressMs: Long) {
    val safeProgress = progressMs.coerceIn(0L, durationMs)
    clearReachedEnd()
    generation.requestSeek(safeProgress)
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
