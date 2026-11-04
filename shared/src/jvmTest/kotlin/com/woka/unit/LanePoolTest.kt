package com.woka.unit

import com.woka.core.ui.image.SourceRange
import com.woka.feature.viewer.video.engine.join.LEAD_HORIZON_MS
import com.woka.feature.viewer.video.engine.join.MAX_LANES
import com.woka.feature.viewer.video.engine.join.continues
import com.woka.feature.viewer.video.engine.join.upcomingStarts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which upcoming ranges get a decoder lane (ME-232) — the pool's plan, without decoders. */
class LanePoolTest {

    /** One-second ranges, each from a different place in the film, so none continues another. */
    private fun scattered(count: Int) = List(count) { i -> SourceRange(i * 10_000L, i * 10_000L + 1_000L) }

    @Test
    fun `only ranges starting inside the horizon get a lane`() {
        val ranges = listOf(SourceRange(0L, 10_000L), SourceRange(20_000L, 21_000L))
        assertEquals(emptyList(), upcomingStarts(ranges, playing = 0, positionMs = 0L))
        assertEquals(listOf(1), upcomingStarts(ranges, playing = 0, positionMs = 10_000L - LEAD_HORIZON_MS))
    }

    @Test
    fun `never more than the pool holds, nearest first`() {
        val wanted = upcomingStarts(scattered(8), playing = 0, positionMs = 900L)
        assertEquals(MAX_LANES, wanted.size)
        assertEquals(listOf(1, 2, 3), wanted)
    }

    @Test
    fun `short ranges bring more of the list inside the horizon`() {
        val short = List(6) { i -> SourceRange(i * 10_000L, i * 10_000L + 400L) }
        val long = List(6) { i -> SourceRange(i * 10_000L, i * 10_000L + 2_000L) }
        assertEquals(listOf(1, 2, 3), upcomingStarts(short, 0, 0L))
        assertEquals(listOf(1), upcomingStarts(long, 0, 0L))
    }

    @Test
    fun `a range at another speed stretches the distance on the result`() {
        val slow = listOf(SourceRange(0L, 2_000L, speed = 0.5f), SourceRange(9_000L, 10_000L))
        // 2 s of source at half speed is 4 s on the result: the next start is past the horizon.
        assertEquals(emptyList(), upcomingStarts(slow, playing = 0, positionMs = 0L))
    }

    @Test
    fun `a freeze counts its hold, and the range after it wants a lane`() {
        val ranges = listOf(SourceRange(5_000L, 5_000L, holdMs = 2_000L), SourceRange(5_000L, 6_000L))
        assertEquals(listOf(1), upcomingStarts(ranges, playing = 0, positionMs = 5_000L))
    }

    @Test
    fun `a range that picks up where the last stopped needs no lane`() {
        val split = listOf(SourceRange(0L, 1_000L), SourceRange(1_000L, 2_000L, speed = 2f), SourceRange(8_000L, 9_000L))
        assertTrue(continues(split[0], split[1]))
        assertEquals(listOf(2), upcomingStarts(split, playing = 0, positionMs = 500L))
    }

    @Test
    fun `nothing continues across a freeze`() {
        val freeze = SourceRange(1_000L, 1_000L, holdMs = 500L)
        assertFalse(continues(SourceRange(0L, 1_000L), freeze))
        assertFalse(continues(freeze, SourceRange(1_000L, 2_000L)))
    }

    @Test
    fun `the last range has nothing after it`() {
        assertEquals(emptyList(), upcomingStarts(scattered(3), playing = 2, positionMs = 20_500L))
    }
}
