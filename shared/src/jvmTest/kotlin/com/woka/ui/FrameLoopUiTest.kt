package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import com.woka.core.data.FrameKind
import com.woka.core.data.ImageFrame
import com.woka.feature.viewer.shell.MediaViewerState
import com.woka.feature.viewer.shell.frames.FramePlaybackEffect
import com.woka.feature.viewer.shell.frames.FrameStripModel
import com.woka.support.onUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Loop (RD-501): On is forever whatever the file says, Off is one pass that stops on the last
 * frame. Split out of `FramePlaybackUiTest`, on the same test clock and the same 20 ms frames.
 */
@OptIn(ExperimentalTestApi::class)
class FrameLoopUiTest {

    private fun animation(count: Int = 3) = FrameStripModel(
        frames = (0 until count).map {
            ImageFrame(it, if (it == 0) FrameKind.Primary else FrameKind.Sequence, durationMs = 20)
        }
    )

    private fun state() = MediaViewerState("D:/Photos/anim.gif")

    /** Loop Off is one pass, and it stops on the last frame (RD-501). */
    @Test
    fun `with Loop off an animation stops at its end`() {
        val state = state().also { it.isFrameLooping = false }
        onUi {
            setContent { FramePlaybackEffect(state, animation()) }
            waitUntil("playback stops") { !state.isFramePlaying }
            assertEquals(2, state.frameIndex, "it stopped somewhere other than the last frame")
        }
    }

    /**
     * Loop On is forever, whatever the file says: the clock no longer reads its repeat
     * count (the author, 2026-11-27). Two wraps prove more than one pass was played.
     */
    @Test
    fun `with Loop on an animation keeps coming round`() {
        val state = state()
        onUi {
            setContent { FramePlaybackEffect(state, animation()) }
            repeat(2) {
                waitUntil("it reaches the last frame") { state.frameIndex == 2 }
                waitUntil("and wraps") { state.frameIndex == 0 }
            }
            assertTrue(state.isFramePlaying)
        }
    }

    /** Switching Loop off mid-run lets the pass finish, then stops on the last frame. */
    @Test
    fun `turning Loop off while it plays stops at the end of the pass`() {
        val state = state()
        onUi {
            setContent { FramePlaybackEffect(state, animation()) }
            waitUntil("the frame advances") { state.frameIndex > 0 }
            state.isFrameLooping = false
            waitUntil("playback stops") { !state.isFramePlaying }
            assertEquals(2, state.frameIndex)
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
        val state = state().also { it.isFrameLooping = false }
        onUi {
            setContent { FramePlaybackEffect(state, animation()) }
            waitUntil("playback stops") { !state.isFramePlaying }

            state.isFramePlaying = true
            waitUntil("it starts over") { state.frameIndex == 0 }
            waitUntil("and runs to the end again") { !state.isFramePlaying }
            assertEquals(2, state.frameIndex)
        }
    }
}
