package com.woka.unit

import com.woka.core.ui.image.EditedClock
import com.woka.core.ui.image.SourceRange
import com.woka.feature.editor.engine.video.StripHandle
import com.woka.feature.editor.engine.video.handlesOf
import com.woka.feature.editor.tools.trim.TrimClip
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The player's slider on a cut film (ME-285, reported 2026-11-02: 26 s trimmed to 10 s
 * still read 0:26), and which edges the strip offers to drag after a split.
 */
class EditedClockTest {

    private val clock = EditedClock(listOf(SourceRange(0L, 4_000L), SourceRange(10_000L, 16_000L)))

    @Test
    fun `the slider is as long as what was kept`() {
        assertEquals(10_000L, clock.totalMs)
        assertEquals(10_127L, EditedClock(listOf(SourceRange(0L, 10_127L))).totalMs)
    }

    @Test
    fun `a source position reads on the result through the range the walk is in`() {
        clock.index = 0
        assertEquals(2_000L, clock.toTimeline(2_000L))
        clock.index = 1
        assertEquals(6_000L, clock.toTimeline(12_000L))
    }

    @Test
    fun `a seek on the result lands in the source and names its range`() {
        assertEquals(0 to 3_000L, clock.toSource(3_000L))
        assertEquals(1 to 11_000L, clock.toSource(5_000L))
        assertEquals(1 to 16_000L, clock.toSource(99_000L))
    }

    @Test
    fun `after a split the selected half has both its own ends`() {
        val clips = listOf(TrimClip("a", 0L, 10_000L), TrimClip("b", 10_000L, 26_000L))
        assertEquals(listOf(StripHandle(0, true), StripHandle(1, false)), handlesOf(clips, null))
        assertEquals(
            listOf(StripHandle(0, true), StripHandle(1, false), StripHandle(0, false)),
            handlesOf(clips, "a")
        )
    }
}
