package com.woka.unit

import com.woka.feature.editor.tools.trim.frameAt
import com.woka.feature.editor.tools.trim.frameCount
import com.woka.feature.editor.tools.trim.secondAndFrame
import com.woka.feature.editor.tools.trim.seekTargetOf
import com.woka.feature.editor.tools.trim.stepPicture
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The result's frame steps (ME-219). The decoder is modelled the way `landAt` behaves — the
 * first picture at or after the target — over pictures whose timestamps sit at any offset
 * from timeline zero, because a cut lands on any millisecond, and whose reported time is
 * truncated to a millisecond, as the presenter's is.
 */
class FrameStepsTest {

    private val rates = listOf(23.976, 24.0, 25.0, 29.97, 30.0, 50.0, 59.94, 60.0)
    private val hour = 3_600_000L

    @Test
    fun stepsShowEveryPictureOnceWhateverTheOffset() {
        for (fps in rates) for (tenth in 0..9) for (lost in 0L..2L) {
            val period = 1000.0 / fps
            val offset = tenth / 10.0 * period
            // The first picture at or after a target, which the mapping may have cut short.
            val shownAt = { target: Long -> ceil((target - lost - offset) / period - 1e-9).toLong() }
            var shown = 10L
            // The first step counts from the picture's reported, truncated time…
            var picture = (shown * period + offset).toLong().toDouble()
            repeat(200) {
                picture = stepPicture(picture, 1L, hour, fps)
                assertEquals(++shown, shownAt(seekTargetOf(picture)), "fps $fps offset $tenth/10 lost $lost")
            }
            // …and every later one from the picture the last step aimed at.
            repeat(100) {
                picture = stepPicture(picture, -1L, hour, fps)
                assertEquals(--shown, shownAt(seekTargetOf(picture)), "fps $fps offset $tenth/10 back")
            }
            picture = stepPicture(picture, 25L, hour, fps)
            assertEquals(shown + 25L, shownAt(seekTargetOf(picture)), "fps $fps a second's step")
        }
    }

    @Test
    fun stepsStopOnTheLastPictureInsideTheResultAndKeepItsLattice() {
        assertEquals(15.0, stepPicture(55.0, -5L, 1000L, 25.0))
        assertEquals(975.0, stepPicture(935.0, 30L, 1000L, 25.0))
        assertEquals(935.0, stepPicture(975.0, -1L, 1000L, 25.0))
        assertEquals(0L, seekTargetOf(2.0))
        assertTrue(seekTargetOf(975.0) in 970L..971L)
    }

    @Test
    fun theReadoutCountsFramesOfTheResult() {
        assertEquals(25L, frameCount(1000L, 25.0))
        assertEquals(12L, frameAt(480L, 25.0))
        assertEquals(0L to 24L, secondAndFrame(24L, 25.0))
        assertEquals(1L to 0L, secondAndFrame(25L, 25.0))
        assertEquals(0L to 23L, secondAndFrame(23L, 23.976))
        assertEquals(1L to 0L, secondAndFrame(24L, 23.976))
        assertEquals(1L to 23L, secondAndFrame(47L, 23.976))
        assertEquals(2L to 0L, secondAndFrame(48L, 23.976))
        assertEquals(1L to 29L, secondAndFrame(59L, 29.97))
        assertEquals(2L to 0L, secondAndFrame(60L, 29.97))
    }
}
