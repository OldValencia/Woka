package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import com.woka.core.data.FrameKind
import com.woka.core.data.ImageFrame
import com.woka.feature.viewer.shell.MediaViewerState
import com.woka.feature.viewer.shell.frames.FramePauseRequestEffect
import com.woka.feature.viewer.shell.frames.FramePlaybackEffect
import com.woka.feature.viewer.shell.frames.FrameStripModel
import com.woka.support.onUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The clock (animated-stills), driven at the level it lives: two composable effects over a state
 * object, with no window, no decoder and no file.
 *
 * **The clock is the test's, not the wall's.** `runComposeUiTest` drives the composition
 * on a test scheduler, so the `delay` inside the effect moves only when the main clock
 * does — `waitUntil` advances it, a real sleep does not. The first version of this test
 * slept and timed out against a player that was working perfectly.
 *
 * Frames declare **20 ms**, the floor the player clamps to, so a case is a handful of
 * virtual ticks.
 */
@OptIn(ExperimentalTestApi::class)
class FramePlaybackUiTest {

    private fun animation(count: Int = 3, repeat: Int? = -1) = FrameStripModel(
        frames = (0 until count).map {
            ImageFrame(it, if (it == 0) FrameKind.Primary else FrameKind.Sequence, durationMs = 20)
        },
        repeatCount = repeat
    )

    /** A burst: frames, no durations. It must behave exactly as it always has. */
    private fun burst() = FrameStripModel(
        frames = (0..2).map { ImageFrame(it, if (it == 0) FrameKind.Primary else FrameKind.Sequence) }
    )

    private fun state() = MediaViewerState("D:/Photos/anim.gif")

    @Test
    fun `an animation advances its frame and comes back round`() {
        val state = state()
        onUi {
            setContent { FramePlaybackEffect(state, animation()) }
            waitUntil("the frame advances") { state.frameIndex > 0 }
            waitUntil("the loop wraps") { state.frameIndex == 0 && state.isFramePlaying }
        }
    }

    @Test
    fun `a burst never advances on its own`() {
        val state = state()
        onUi {
            setContent { FramePlaybackEffect(state, burst()) }
            mainClock.advanceTimeBy(500)
            assertEquals(0, state.frameIndex, "a set with no durations was played")
        }
    }

    @Test
    fun `a paused animation stands still`() {
        val state = state().also { it.isFramePlaying = false }
        onUi {
            setContent { FramePlaybackEffect(state, animation()) }
            mainClock.advanceTimeBy(500)
            assertEquals(0, state.frameIndex)
        }
    }

    /** `repeatCount = 0` is skia's "play once", and it stops on the last frame. */
    @Test
    fun `a file that does not loop stops at its end`() {
        val state = state()
        onUi {
            setContent { FramePlaybackEffect(state, animation(repeat = 0)) }
            waitUntil("playback stops") { !state.isFramePlaying }
            assertEquals(2, state.frameIndex, "it stopped somewhere other than the last frame")
        }
    }

    /**
     * And pressing play *there* has to show it again. The clock counts its remaining
     * passes from where it resumes, so without a rewind the first step wraps, spends
     * the last pass and stops on the frame it started from — a play button that
     * visibly does nothing.
     */
    @Test
    fun `a finished animation plays again when asked`() {
        val state = state()
        onUi {
            setContent { FramePlaybackEffect(state, animation(repeat = 0)) }
            waitUntil("playback stops") { !state.isFramePlaying }

            state.isFramePlaying = true
            waitUntil("it starts over") { state.frameIndex == 0 }
            waitUntil("and runs to the end again") { !state.isFramePlaying }
            assertEquals(2, state.frameIndex)
        }
    }

    @Test
    fun `the play-pause request toggles an animation and ignores a burst`() {
        val state = state()
        onUi {
            setContent { FramePauseRequestEffect(state, animation()) }
            waitForIdle()
            assertTrue(state.isFramePlaying, "a raise that never happened toggled something")

            state.playPauseEvent.raise()
            waitForIdle()
            assertFalse(state.isFramePlaying)

            state.playPauseEvent.raise()
            waitForIdle()
            assertTrue(state.isFramePlaying, "two taps must be two toggles")
        }
        val still = state()
        onUi {
            setContent { FramePauseRequestEffect(still, burst()) }
            still.playPauseEvent.raise()
            waitForIdle()
            assertTrue(still.isFramePlaying, "a burst has no clock to pause")
        }
    }
}
