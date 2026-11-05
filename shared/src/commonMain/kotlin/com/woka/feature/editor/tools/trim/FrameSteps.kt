package com.woka.feature.editor.tools.trim

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Frames of the result, as pure functions over timeline milliseconds (ME-219).
 *
 * **A frame of the result is one source frame period of the timeline**, at the source's
 * rate — the grid a render encodes on (`RenderPace`), so a ¼× clip steps through each
 * source picture four times and a 4× clip skips three, exactly as the output will.
 *
 * **A step is relative to the picture on screen, never to a grid.** Pictures sit at any
 * offset from timeline zero, because a cut lands on any millisecond, and both the picture's
 * own time and the timeline ↔ source mapping are truncated to a millisecond each. Measured
 * 2026-11-05 (`FrameStepsTest`): read back onto a fixed grid, one step in twenty skipped a
 * picture at 25 fps. So the next picture is *this one plus a period*, kept as a `Double`
 * so it never drifts, and the seek aims [MARGIN_MS] before it — the player shows the first
 * picture at or after its target (`landAt`).
 */
private const val MARGIN_MS = 5.0

/** Which frame of the result timeline position [timelineMs] shows — for the readout. */
fun frameAt(timelineMs: Long, fps: Double): Long =
    (timelineMs * fps / 1000.0).roundToLong().coerceAtLeast(0L)

/** How many frames a result [totalMs] long holds. */
fun frameCount(totalMs: Long, fps: Double): Long = (totalMs * fps / 1000.0).roundToLong()

/**
 * The time of the picture [frames] frames from the picture at [pictureMs]. Past either end
 * it stops on the last whole period inside the result, so the picture lattice is kept and
 * the step after it does not repeat a picture.
 */
fun stepPicture(pictureMs: Double, frames: Long, totalMs: Long, fps: Double): Double {
    val period = 1000.0 / fps
    val wanted = pictureMs + frames * period
    return when {
        wanted < 0.0 -> pictureMs - floor(pictureMs / period) * period
        wanted > totalMs - 1.0 -> pictureMs + floor((totalMs - 1.0 - pictureMs) / period) * period
        else -> wanted
    }
}

/** Where to seek so that the picture at [pictureMs] is the one shown. */
fun seekTargetOf(pictureMs: Double): Long = ceil(pictureMs - MARGIN_MS).toLong().coerceAtLeast(0L)

/** The frames in one second, for an `Alt` step. */
fun framesPerSecond(fps: Double): Long = fps.roundToLong().coerceAtLeast(1L)

/**
 * Frame [frame] as whole seconds plus the frame inside that second — `ss` and `ff` of a
 * `m:ss:ff` timecode. The second begins at the first frame on or after its own instant,
 * so a 23.976 film counts `00…23` and turns over on the same frame a clock would.
 */
fun secondAndFrame(frame: Long, fps: Double): Pair<Long, Long> {
    val second = floor((frame + 1e-6) / fps).toLong()
    return second to frame - ceil(second * fps - 1e-6).toLong()
}
