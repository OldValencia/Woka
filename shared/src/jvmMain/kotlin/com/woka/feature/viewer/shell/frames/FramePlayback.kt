package com.woka.feature.viewer.shell.frames

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.woka.core.data.ImageFrame
import com.woka.feature.viewer.shell.MediaViewerState
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
 * The clock an animated GIF or WebP has never had (ME-140).
 *
 * **It moves one number.** `MediaViewerState.frameIndex` already says which frame is on
 * screen and the decode effect is already keyed on it, so playing an animation is
 * advancing that index on the frame's own timing — no second decode path, no player, and
 * the frame strip keeps working exactly as it did because it reads the same number.
 *
 * **Nothing runs for a still.** A photograph, a HEIC burst and a multi-page TIFF have no
 * durations ([FrameStripModel.isAnimated] is false), and this returns before its first
 * delay — the common case pays a comparison.
 *
 * The first pass is as slow as the decoder: each frame is decoded when it is due, and skia
 * has to walk earlier frames to build a later one. From the second pass on every frame is
 * an `ImageBitmapCache` hit, so a loop settles into its declared speed. Pre-decoding ahead
 * of the clock would be the fix if that first pass ever grates; it is not built until it
 * does, because it costs a decode nobody may watch.
 */
@Composable
internal fun FramePlaybackEffect(state: MediaViewerState, strip: FrameStripModel) {
    LaunchedEffect(state.activeItemPath, strip, state.isFramePlaying) {
        if (!strip.isAnimated || !state.isFramePlaying) return@LaunchedEffect
        val frames = strip.frames
        // Passes **left**, counted from where playback resumes rather than from the
        // beginning: a paused-and-resumed animation that had used up its repeats would
        // otherwise stop the moment it was resumed.
        var passesLeft = strip.repeatCount ?: -1

        while (true) {
            val current = frames.firstOrNull { it.index == state.frameIndex } ?: return@LaunchedEffect
            delay(current.delayMs().toLong().milliseconds)

            val next = frames.nextAfter(state.frameIndex)
            if (next == null || next.index <= state.frameIndex) {
                // A pass ended. `-1` is forever; anything else is spent one at a time and
                // stops on the **last** frame, which is where a non-looping animation ends.
                if (passesLeft == 0) {
                    state.isFramePlaying = false
                    return@LaunchedEffect
                }
                if (passesLeft > 0) passesLeft--
            }
            state.frameIndex = next?.index ?: frames.first().index
        }
    }
}

/**
 * "Play/pause was asked for" — by a click on the picture or by Space, which raise the
 * same event (`MediaViewerState.playPauseEvent`).
 *
 * Keyed on the counter rather than on a boolean, because two taps in a row have to be
 * two toggles; `ViewerEvent` exists for exactly that and its own doc says why a
 * timestamp did not work. Seq 0 is "never raised" and must not toggle anything on the
 * first composition.
 */
@Composable
internal fun FramePauseRequestEffect(state: MediaViewerState, strip: FrameStripModel) {
    val seq = state.playPauseEvent.seq
    LaunchedEffect(seq) {
        if (seq == 0 || !strip.isAnimated) return@LaunchedEffect
        state.isFramePlaying = !state.isFramePlaying
    }
}

private fun ImageFrame.delayMs(): Int =
    (durationMs ?: DEFAULT_FRAME_MS).coerceAtLeast(MINIMUM_FRAME_MS)

/**
 * The next frame **with a duration**, wrapping to the first.
 *
 * A Live Photo's motion entry sits at the end of the strip carrying no duration, and
 * running the clock into it would try to play a `.mov` as a still. Skipping frames that
 * declare nothing keeps the loop inside the animation it belongs to.
 */
private fun List<ImageFrame>.nextAfter(index: Int): ImageFrame? {
    val playable = filter { (it.durationMs ?: 0) > 0 }
    if (playable.isEmpty()) return null
    return playable.firstOrNull { it.index > index } ?: playable.first()
}
