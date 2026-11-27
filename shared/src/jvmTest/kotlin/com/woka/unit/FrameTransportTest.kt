package com.woka.unit

import com.woka.core.data.FrameKind
import com.woka.core.data.ImageFrame
import com.woka.core.ui.components.frames.FrameTransport
import com.woka.core.ui.components.frames.durationMs
import com.woka.core.ui.components.frames.positionMs
import com.woka.core.ui.components.frames.repeatCountFor
import com.woka.core.ui.components.frames.seekTo
import com.woka.core.ui.components.frames.step
import com.woka.core.ui.components.frames.togglePlaying
import com.woka.feature.viewer.video.formatTenths
import com.woka.feature.viewer.video.leadingLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An animation's transport (RD-501) as plain arithmetic: where the frame on screen sits in
 * time, how long one pass is, and what a step and a seek do to the clock.
 */
class FrameTransportTest {

    private class Transport(override val frames: List<ImageFrame>) : FrameTransport {
        override var frameIndex = 0
        override var isPlaying = true
        override var loop = true
        override var speed = 1f
        override var showFrameNumbers = false
    }

    /** 100, 5 (clamped to 20), 200 ms, and a Live Photo's untimed motion entry at the end. */
    private fun transport() = Transport(
        listOf(
            ImageFrame(0, FrameKind.Primary, 100),
            ImageFrame(1, FrameKind.Sequence, 5),
            ImageFrame(2, FrameKind.Sequence, 200),
            ImageFrame(3, FrameKind.Motion, null)
        )
    )

    @Test
    fun `time counts the timed frames, with the clock's 20 ms floor`() {
        val transport = transport()
        assertEquals(320L, transport.durationMs(), "one pass is 100 + 20 + 200")
        transport.frameIndex = 2
        assertEquals(120L, transport.positionMs(), "frame 2 appears after 100 + 20")
    }

    @Test
    fun `a step pauses and stops at either end`() {
        val transport = transport()
        transport.step(1)
        assertFalse(transport.isPlaying, "a step left the clock running")
        assertEquals(1, transport.frameIndex)

        transport.step(5)
        assertEquals(2, transport.frameIndex, "a step walked past the last timed frame")
        transport.step(-9)
        assertEquals(0, transport.frameIndex, "a step wrapped instead of stopping")
    }

    @Test
    fun `a seek pauses on the frame it reached for`() {
        val transport = transport()
        transport.seekTo(2)
        assertFalse(transport.isPlaying)
        assertEquals(2, transport.frameIndex)
        transport.togglePlaying()
        assertTrue(transport.isPlaying)
    }

    @Test
    fun `the leading label reads time or frame number`() {
        val transport = transport().also { it.frameIndex = 2 }
        assertEquals("0:00.1", transport.leadingLabel())
        transport.showFrameNumbers = true
        assertEquals("3 / 3", transport.leadingLabel(), "the motion entry is not a frame of the animation")
    }

    @Test
    fun `tenths read m ss d`() {
        assertEquals("0:00.0", formatTenths(0))
        assertEquals("0:01.6", formatTenths(1_650))
        assertEquals("1:05.2", formatTenths(65_200))
    }

    @Test
    fun `Loop is forever or one pass`() {
        assertEquals(-1, repeatCountFor(loop = true))
        assertEquals(0, repeatCountFor(loop = false))
    }
}
