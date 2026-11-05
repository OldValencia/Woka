package com.woka.unit

import com.woka.core.ui.image.EditedClock
import com.woka.core.ui.image.SourceRange
import com.woka.feature.editor.tools.trim.FREEZE_HOLD_MS
import com.woka.feature.editor.tools.trim.FREEZE_MAX_MS
import com.woka.feature.editor.tools.trim.atTimeline
import com.woka.feature.editor.tools.trim.freezeAt
import com.woka.feature.editor.tools.trim.join
import com.woka.feature.editor.tools.trim.totalMs
import com.woka.feature.editor.tools.trim.wholeSource
import com.woka.feature.editor.tools.trim.withHold
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A freeze-frame (ME-217): a clip of one frame plus a hold. It is inserted at the play head,
 * lasts its hold on the timeline, shows its one frame throughout, and joins nothing.
 */
class FreezeFrameTest {

    private val frozen = wholeSource(10_000L).freezeAt(4_000L, "f", "r")

    @Test
    fun `a freeze inside a clip splits it and sits between the halves`() {
        assertEquals(listOf("source", "f", "r"), frozen.map { it.id })
        val freeze = frozen[1]
        assertEquals(4_000L to 4_000L, freeze.fromMs to freeze.toMs)
        assertEquals(FREEZE_HOLD_MS, freeze.lengthMs)
        assertEquals(10_000L + FREEZE_HOLD_MS, frozen.totalMs())
    }

    /** 1.0.6 review: too near the end to split, it went to the start of the clip. */
    @Test
    fun `a freeze near a clip's end lands after it, and the film's end freezes its last frame`() {
        val nearEnd = wholeSource(10_000L).freezeAt(9_950L, "f", "r")
        assertEquals(listOf("source", "f"), nearEnd.map { it.id })
        assertEquals(9_950L, nearEnd[1].fromMs)

        val atEnd = wholeSource(10_000L).freezeAt(10_000L, "f", "r")
        assertEquals(listOf("source", "f"), atEnd.map { it.id })
        assertTrue(atEnd[1].fromMs < 10_000L, "a freeze of a frame past the clip's exclusive end")
    }

    @Test
    fun `the whole hold shows the one frame, and the film resumes after it`() {
        assertEquals(4_000L, frozen.atTimeline(4_500L)!!.sourceMs)
        assertEquals(4_000L, frozen.atTimeline(5_900L)!!.sourceMs)
        assertEquals(5_000L, frozen.atTimeline(7_000L)!!.sourceMs, "a second after the hold")
    }

    @Test
    fun `a hold is dragged within its bounds, and a freeze joins nothing`() {
        assertEquals(FREEZE_MAX_MS, frozen.withHold("f", 99 * 60_000L)[1].holdMs)
        assertTrue(frozen.withHold("f", 0L)[1].holdMs > 0L, "a freeze dragged to nothing")
        assertSame(frozen, frozen.join("f"))
        assertSame(frozen, frozen.freezeAt(4_500L, "g", "h"), "a freeze frozen again")
    }

    @Test
    fun `the viewer's clock holds through a freeze`() {
        val clock = EditedClock(frozen.map { SourceRange(it.fromMs, it.toMs, it.key, it.speed, it.holdMs) })
        assertEquals(12_000L, clock.totalMs)
        assertEquals(1 to 4_000L, clock.toSource(5_000L))
        clock.index = 1
        clock.heldMs = 700L
        assertEquals(4_700L, clock.toTimeline(4_000L))
    }
}
