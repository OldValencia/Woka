package com.woka.unit

import com.woka.feature.viewer.video.engine.preview.SeekPreviewGrid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The clamping is where the edge cases live, and it is pure arithmetic. */
class SeekPreviewGridTest {

    @Test
    fun `step is clamped at the short end`() {
        // 10 minutes / 300 would be 2s; the floor holds it at 5s.
        assertEquals(5_000L, SeekPreviewGrid(10 * 60_000L).stepMs)
    }

    @Test
    fun `step is clamped at the long end`() {
        // 4 hours / 300 would be 48s; the ceiling holds it at 30s.
        assertEquals(30_000L, SeekPreviewGrid(4 * 3_600_000L).stepMs)
    }

    @Test
    fun `step is derived between the clamps`() {
        assertEquals(12_000L, SeekPreviewGrid(3_600_000L).stepMs)
        // 7 762 000 / 300 = 25 873, and integer division is what the class does.
        assertEquals(25_873L, SeekPreviewGrid(7_762_000L).stepMs)
    }

    @Test
    fun `an unknown duration is not usable`() {
        assertFalse(SeekPreviewGrid(0L).isUsable)
        assertTrue(SeekPreviewGrid(1L).isUsable)
    }

    @Test
    fun `midpoints stay inside the file`() {
        val duration = 10 * 60_000L
        val grid = SeekPreviewGrid(duration)
        val lastCell = grid.cellOf(duration)
        (0..lastCell).forEach { cell ->
            val midpoint = grid.midpointOf(cell)
            assertTrue(midpoint in 0 until duration, "cell $cell landed at $midpoint")
        }
    }

    @Test
    fun `the last cell is shortened to the duration rather than overrunning it`() {
        // 32s of a 5s grid: the final cell spans 30s..32s, so its midpoint is 31s.
        val grid = SeekPreviewGrid(32_000L)
        assertEquals(6, grid.cellOf(31_500L))
        assertEquals(31_000L, grid.midpointOf(6))
    }

    @Test
    fun `positions outside the file are pulled back into it`() {
        val grid = SeekPreviewGrid(60_000L)
        assertEquals(0, grid.cellOf(-5_000L))
        assertEquals(grid.cellOf(59_999L), grid.cellOf(120_000L))
    }
}
