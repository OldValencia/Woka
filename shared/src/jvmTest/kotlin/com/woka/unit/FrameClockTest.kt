package com.woka.unit

import com.woka.core.data.FrameKind
import com.woka.core.data.ImageFrame
import com.woka.core.data.playFrames
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The clock itself, with no composition and no pictures — the arithmetic the viewer's
 * frame index and the editor's canvas both run on.
 *
 * Driven on `runTest`'s virtual clock: `advanceTimeBy` is what moves the `delay` inside
 * the loop, and a real sleep proves nothing here. Every case is a handful of ticks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FrameClockTest {

    private fun animation(count: Int = 3, durationMs: Int? = 20) = (0 until count).map {
        ImageFrame(it, if (it == 0) FrameKind.Primary else FrameKind.Sequence, durationMs)
    }

    /** A run of the clock, collecting every frame it asked for. */
    private class Run {
        var index = 0
        val shown = mutableListOf<Int>()
        var finished = false

        fun show(next: Int) {
            index = next
            shown += next
        }
    }

    @Test
    fun `an animation advances and comes back round`() = runTest {
        val run = Run()
        val job = launchClock(run, animation())
        advanceTimeBy(85)
        job.cancel()

        assertEquals(listOf(1, 2, 0, 1), run.shown, "the loop did not walk its frames in order")
    }

    @Test
    fun `a set with no timings is not a clock at all`() = runTest {
        val run = Run()
        val job = launchClock(run, animation(durationMs = null))
        advanceTimeBy(500)

        assertTrue(job.isCompleted, "a burst kept a clock running")
        assertEquals(emptyList(), run.shown)
    }

    @Test
    fun `a single frame never advances`() = runTest {
        val run = Run()
        launchClock(run, animation(count = 1))
        advanceTimeBy(500)

        assertEquals(emptyList(), run.shown)
    }

    /** Skia's `0` is "play once", and it ends on the **last** frame. */
    @Test
    fun `a file that does not loop stops at its end`() = runTest {
        val run = Run()
        launchClock(run, animation(), repeatCount = 0)
        advanceTimeBy(500)

        assertEquals(2, run.index, "it stopped somewhere other than the last frame")
        assertTrue(run.finished)
    }

    /**
     * And starting it *there* has to show it again. Without the rewind the clock wraps
     * on its first step, spends its last pass and stops without having moved.
     */
    @Test
    fun `a finished animation rewinds when it is started again`() = runTest {
        val run = Run().also { it.index = 2 }
        launchClock(run, animation(), repeatCount = 0)
        advanceTimeBy(500)

        assertEquals(0, run.shown.first(), "it did not rewind before playing")
        assertEquals(2, run.index)
    }

    /**
     * `n` is *extra* passes, so `1` walks the frames twice and no more.
     *
     * The frame it starts on is never handed to `show` — it is already on screen — so
     * two passes over three frames are the five moves below, not six.
     */
    @Test
    fun `a numbered repeat count is spent one pass at a time`() = runTest {
        val run = Run()
        launchClock(run, animation(), repeatCount = 1)
        advanceTimeBy(500)

        assertEquals(listOf(1, 2, 0, 1, 2), run.shown, "it did not play exactly twice")
        assertTrue(run.finished)
    }

    /**
     * A Live Photo's motion entry is appended to the strip with no duration, and the
     * clock must not walk into it — the movement is a different file.
     */
    @Test
    fun `a frame with no duration is not in the loop`() = runTest {
        val run = Run()
        val withMotion = animation() + ImageFrame(3, FrameKind.Motion, durationMs = null)
        val job = launchClock(run, withMotion)
        advanceTimeBy(85)
        job.cancel()

        assertFalse(run.shown.contains(3), "the clock played the motion entry")
    }

    private fun TestScope.launchClock(
        run: Run,
        frames: List<ImageFrame>,
        repeatCount: Int? = -1
    ) = launch {
        playFrames(
            frames = frames,
            repeatCount = repeatCount,
            frameAt = { run.index },
            show = run::show,
            onFinished = { run.finished = true }
        )
    }
}
