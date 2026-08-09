package com.woka.unit

import com.woka.feature.viewer.video.engine.finishedFromMs
import com.woka.feature.viewer.video.engine.watchFloorMs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val MINUTE = 60_000L

/**
 * Where "watched" begins, for files of wildly different lengths.
 *
 * The flat 30-second tail this replaces was a rounding error at the end of a film
 * and half of a one-minute clip — a 60s video was marked fully watched at 0:30, and
 * the grid drew a full bar for it.
 */
class PlaybackResumeThresholdTest {

    /** What share of the file has to be seen before it counts as watched. */
    private fun shareOf(durationMs: Long): Float =
        finishedFromMs(durationMs).toFloat() / durationMs

    @Test
    fun `a short clip is not finished halfway through it`() {
        assertTrue(shareOf(MINUTE) > 0.85f, "a minute-long clip finishes at ${shareOf(MINUTE)}")
        assertTrue(shareOf(20_000L) > 0.85f)
        assertTrue(shareOf(8_000L) > 0.85f)
    }

    @Test
    fun `a film still finishes within its last half-minute`() {
        val film = 2 * 60 * MINUTE
        assertEquals(film - 30_000L, finishedFromMs(film))
        assertTrue(shareOf(film) > 0.99f)
    }

    @Test
    fun `the ratio governs short files and the flat tail governs long ones`() {
        // The crossover is where 8% of the duration reaches 30s: 6m15s.
        assertEquals(60_000L - 4_800L, finishedFromMs(MINUTE))
        assertEquals(30 * MINUTE - 30_000L, finishedFromMs(30 * MINUTE))
    }

    @Test
    fun `an unknown duration finishes nothing`() {
        assertEquals(Long.MAX_VALUE, finishedFromMs(0L))
        assertEquals(Long.MAX_VALUE, finishedFromMs(-1L))
    }


    @Test
    fun `the floor scales too, so a short clip is resumable at all`() {
        // A flat ten seconds is half a twenty-second clip: it was stored and drawn
        // on the card, then ignored on open, which reads as the setting not working.
        assertEquals(10_000L, watchFloorMs(2 * 60 * MINUTE), "a film's floor should not move")
        assertTrue(watchFloorMs(20_000L) < 8_000L, "8s into a 20s clip must be resumable")
        assertTrue(watchFloorMs(MINUTE) < 20_000L, "20s into a minute must be resumable")
    }

    @Test
    fun `every length has a resumable middle`() {
        listOf(8_000L, 20_000L, MINUTE, 10 * MINUTE, 2 * 60 * MINUTE).forEach { duration ->
            val middle = duration / 2
            assertTrue(
                middle in watchFloorMs(duration) until finishedFromMs(duration),
                "halfway through a ${duration}ms file is not resumable"
            )
        }
    }
}
