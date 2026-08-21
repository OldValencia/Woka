package com.woka.core.data

import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * A frame that declares no duration, or declares nothing sensible, still has to advance —
 * a GIF written with a zero delay means "as fast as is reasonable", and every browser
 * reads that as about ten frames a second rather than as a busy loop.
 */
private const val DEFAULT_FRAME_MS = 100

/** Below this, GIFs in the wild mean "the shortest you will actually draw". */
private const val MINIMUM_FRAME_MS = 20

/**
 * The clock an animation runs on, as a suspend loop over nothing but frames.
 *
 * **It owns no state and knows no UI.** The viewer drives `MediaViewerState.frameIndex`
 * with it and the editor drives a local index; both were about to hold a copy of the
 * repeat arithmetic, which is one rule with two places to drift. What is left in each
 * caller is where the number lives and what redraws when it moves.
 *
 * [frameAt] is read rather than passed, because the caller's index can move underneath
 * this — a frame chosen by hand in the viewer's strip — and the loop has to see it.
 * [onFinished] fires only for an animation whose passes are numbered and spent.
 *
 * Returns immediately for anything that is not an animation: a photograph, a HEIC burst
 * and a multi-page TIFF have frames and no timings, so they never get a clock at all.
 */
suspend fun playFrames(
    frames: List<ImageFrame>,
    repeatCount: Int?,
    frameAt: () -> Int,
    show: (Int) -> Unit,
    onFinished: () -> Unit = {}
) {
    // A Live Photo's motion entry sits at the end of the strip carrying no duration,
    // and running the clock into it would try to play a `.mov` as a still. Frames that
    // declare nothing are not in the loop at all.
    val playable = frames.filter { (it.durationMs ?: 0) > 0 }
    if (playable.size < 2) return

    var passesLeft = repeatCount ?: -1
    // A pass-limited animation stops on its last frame. Starting *there* is a request
    // to see it again, so it rewinds first — without this the clock wraps on its very
    // first step, spends its last pass and stops without having moved.
    if (passesLeft >= 0 && frameAt() >= playable.last().index) show(playable.first().index)

    while (true) {
        val current = playable.firstOrNull { it.index == frameAt() } ?: return
        delay(current.delayMs().toLong().milliseconds)

        val next = playable.nextAfter(frameAt())
        if (next.index <= frameAt()) {
            // A pass ended. `-1` is forever; anything else is spent one at a time and
            // stops on the **last** frame, which is where a non-looping animation ends.
            if (passesLeft == 0) {
                onFinished()
                return
            }
            if (passesLeft > 0) passesLeft--
        }
        show(next.index)
    }
}

private fun ImageFrame.delayMs(): Int =
    (durationMs ?: DEFAULT_FRAME_MS).coerceAtLeast(MINIMUM_FRAME_MS)

/**
 * The next frame after [index], wrapping to the first. Called on a list that is
 * already only the playable frames and never empty, so it always answers.
 */
private fun List<ImageFrame>.nextAfter(index: Int): ImageFrame =
    firstOrNull { it.index > index } ?: first()
