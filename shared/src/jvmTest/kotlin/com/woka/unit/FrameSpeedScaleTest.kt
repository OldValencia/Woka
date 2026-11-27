package com.woka.unit

import com.woka.core.ui.components.frames.FrameSpeedScale
import kotlin.test.Test
import kotlin.test.assertEquals

/** An animation's speed track (RD-501): 0.25–5×, 1× at the centre, detents that pull. */
class FrameSpeedScaleTest {

    @Test
    fun `the ends and the centre`() {
        assertEquals(0f, FrameSpeedScale.fractionOf(0.25f))
        assertEquals(0.5f, FrameSpeedScale.fractionOf(1f), "1x is not at the centre")
        assertEquals(1f, FrameSpeedScale.fractionOf(5f))
        assertEquals(1f, FrameSpeedScale.speedAt(0.5f))
    }

    @Test
    fun `every detent maps back onto itself`() {
        FrameSpeedScale.DETENTS.forEach {
            assertEquals(it, FrameSpeedScale.speedAt(FrameSpeedScale.fractionOf(it)), "$it× did not round-trip")
        }
    }

    @Test
    fun `a detent pulls, and between them the speed is a 0_05 step`() {
        assertEquals(1.5f, FrameSpeedScale.speedAt(FrameSpeedScale.fractionOf(1.53f)), "1.53 was not pulled onto 1.5")
        assertEquals(1.25f, FrameSpeedScale.speedAt(FrameSpeedScale.fractionOf(1.27f)))
        assertEquals(2.5f, FrameSpeedScale.speedAt(FrameSpeedScale.fractionOf(2.5f)), "a free value moved")
    }
}
