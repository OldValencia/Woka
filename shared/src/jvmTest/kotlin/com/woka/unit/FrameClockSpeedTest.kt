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

/**
 * The clock's speed (RD-501): the transport's slider divides every delay, and is read
 * before each one rather than once. Beside `FrameClockTest`, on the same virtual clock and
 * the same 20 ms frames.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FrameClockSpeedTest {

    private val frames = (0 until 3).map {
        ImageFrame(it, if (it == 0) FrameKind.Primary else FrameKind.Sequence, durationMs = 20)
    }

    private val shown = mutableListOf<Int>()
    private var index = 0

    private fun TestScope.launchClock(speed: () -> Float) = launch {
        playFrames(
            frames = frames,
            repeatCount = -1,
            frameAt = { index },
            show = { index = it; shown += it },
            speed = speed
        )
    }

    @Test
    fun `twice the speed halves the delays`() = runTest {
        val job = launchClock { 2f }
        advanceTimeBy(45)
        job.cancel()

        assertEquals(listOf(1, 2, 0, 1), shown)
    }

    @Test
    fun `half the speed doubles the delays`() = runTest {
        val job = launchClock { 0.5f }
        advanceTimeBy(85)
        job.cancel()

        assertEquals(listOf(1, 2), shown)
    }

    /** 1 at 20 ms and 2 at 40 ms at 1×; from there 10 ms apart: 0 at 50, 1 at 60. */
    @Test
    fun `a speed change takes effect from the next frame`() = runTest {
        var speed = 1f
        val job = launchClock { speed }
        advanceTimeBy(25)
        assertEquals(listOf(1), shown)
        speed = 2f
        advanceTimeBy(40)
        job.cancel()

        assertEquals(listOf(1, 2, 0, 1), shown, "the faster pace did not apply")
    }
}
