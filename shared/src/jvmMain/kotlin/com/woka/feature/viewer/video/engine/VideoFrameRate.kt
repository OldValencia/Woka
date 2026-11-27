package com.woka.feature.viewer.video.engine

/** When the container does not say: the render's own fallback. */
private const val FALLBACK_FPS = 30.0

/**
 * The source's frame rate, or [FALLBACK_FPS] when the container does not say. Moved here
 * from the editor's `ClipWalk` when the viewer learned to step a frame too (RD-501): the
 * editor's timeline and the viewer's `,` / `.` step on the same period.
 */
fun VideoPlayerState.frameRate(): Double =
    tracks.mediaTracks.video.firstOrNull { it.index == tracks.selectedVideoIndex }
        ?.frameRate?.takeIf { it.isFinite() && it > 1.0 } ?: FALLBACK_FPS
