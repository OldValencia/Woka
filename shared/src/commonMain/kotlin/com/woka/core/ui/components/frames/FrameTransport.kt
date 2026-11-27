package com.woka.core.ui.components.frames

import androidx.compose.runtime.Stable
import com.woka.core.data.ImageFrame
import com.woka.core.data.frameDelayMs

/**
 * An animation's transport (RD-501): the one clock the bar, the strip and the keys move.
 *
 * **An interface, because the number lives in two places.** The viewer's frame index is
 * `MediaViewerState.frameIndex`, which a burst's strip and `PageUp`/`PageDown` move too;
 * the editor's is its playback, which must not reopen the engine on every frame. Each
 * adapts its own state, and the bar learns neither.
 *
 * [loop] opens On and [speed] at 1× for every animation (the author, 2026-11-27): a GIF is a
 * looping picture, and the file's own repeat count is not read by the clock.
 */
@Stable
interface FrameTransport {
    /** The file's frames; the timed ones are [playable]. Empty until they are read. */
    val frames: List<ImageFrame>
    var frameIndex: Int
    var isPlaying: Boolean
    var loop: Boolean
    var speed: Float

    /** The leading label reads `n / N` instead of the time. */
    var showFrameNumbers: Boolean
}

/** The frames the clock walks — the same filter `playFrames` makes. */
val FrameTransport.playable: List<ImageFrame>
    get() = frames.filter { (it.durationMs ?: 0) > 0 }

/** Where [frameIndex] stands among [playable], or 0 when it is not one of them. */
fun FrameTransport.playablePosition(): Int =
    playable.indexOfFirst { it.index == frameIndex }.coerceAtLeast(0)

/** The time at which the frame on screen appears, at 1×. */
fun FrameTransport.positionMs(): Long =
    playable.takeWhile { it.index != frameIndex }.sumOf { it.frameDelayMs().toLong() }

/** One pass of the animation, at 1×. */
fun FrameTransport.durationMs(): Long = playable.sumOf { it.frameDelayMs().toLong() }

/** Pauses and moves [delta] frames, stopping at either end — a step never wraps. */
fun FrameTransport.step(delta: Int) {
    val walk = playable
    isPlaying = false
    if (walk.isEmpty()) return
    frameIndex = walk[(playablePosition() + delta).coerceIn(0, walk.lastIndex)].index
}

/** Reaching for a frame is asking the animation to stand still on it. */
fun FrameTransport.seekTo(index: Int) {
    isPlaying = false
    frameIndex = index
}

fun FrameTransport.togglePlaying() {
    isPlaying = !isPlaying
}

/** `playFrames`' count for the Loop switch: forever, or one pass that stops on the last frame. */
fun repeatCountFor(loop: Boolean): Int = if (loop) -1 else 0
