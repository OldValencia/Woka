package com.woka.unit

import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.engine.video.CLIP_SPEED
import com.woka.feature.editor.shell.snapToDetent
import com.woka.feature.editor.shell.trackFraction
import com.woka.feature.editor.shell.valueAtFraction
import kotlin.test.Test
import kotlin.test.assertEquals

/** Where a value sits along a scrub row (RD-302): linear, or in octaves for the clip's speed. */
class ScrubScaleTest {

    private val volume = FloatParam("volume", "Volume", 0f..200f, default = 100f)

    @Test
    fun `the speed row puts 1x at the centre and 2x and a half as far either side`() {
        assertEquals(0.5f, trackFraction(CLIP_SPEED, 1f), 1e-4f)
        assertEquals(0.75f, trackFraction(CLIP_SPEED, 2f), 1e-4f)
        assertEquals(0.25f, trackFraction(CLIP_SPEED, 0.5f), 1e-4f)
        assertEquals(0f, trackFraction(CLIP_SPEED, 0.25f), 1e-4f)
        assertEquals(1f, trackFraction(CLIP_SPEED, 4f), 1e-4f)
    }

    @Test
    fun `a point of the speed row is read back as its speed`() {
        assertEquals(0.5f, valueAtFraction(CLIP_SPEED, 0.25f), 1e-4f)
        assertEquals(2f, valueAtFraction(CLIP_SPEED, 0.75f), 1e-4f)
        assertEquals(4f, valueAtFraction(CLIP_SPEED, 1.5f), 1e-4f)
    }

    @Test
    fun `a linear row is unchanged`() {
        assertEquals(0.5f, trackFraction(volume, 100f), 1e-4f)
        assertEquals(150f, valueAtFraction(volume, 0.75f), 1e-3f)
    }

    @Test
    fun `the default holds a value passing near it, on either scale`() {
        assertEquals(100f, snapToDetent(volume, 102f))
        assertEquals(110f, snapToDetent(volume, 110f))
        assertEquals(1f, snapToDetent(CLIP_SPEED, 1.02f))
        assertEquals(1.2f, snapToDetent(CLIP_SPEED, 1.2f))
    }
}
