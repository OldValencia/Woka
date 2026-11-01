package com.woka.unit

import com.woka.feature.viewer.video.engine.probe.clockwiseQuarterTurns
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * VF-105: FFmpeg's counter-clockwise display rotation as clockwise quarter turns,
 * the mapping matched against the CLI's autorotate (feature doc §8).
 */
class DisplayRotationTest {

    @Test
    fun `a display matrix angle maps to the turn the CLI applies`() {
        assertEquals(0, clockwiseQuarterTurns(0.0))
        assertEquals(3, clockwiseQuarterTurns(90.0), "90 is shown with transpose=cclock")
        assertEquals(1, clockwiseQuarterTurns(-90.0), "−90 is shown with transpose=clock")
        assertEquals(1, clockwiseQuarterTurns(270.0))
        assertEquals(2, clockwiseQuarterTurns(180.0))
        assertEquals(2, clockwiseQuarterTurns(-180.0))
        assertEquals(3, clockwiseQuarterTurns(89.97), "a matrix rounds to the nearest degree")
        assertEquals(0, clockwiseQuarterTurns(Double.NaN), "no matrix is no turn")
    }
}
