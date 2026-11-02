package com.woka.unit

import com.woka.feature.editor.tools.trim.MIN_CLIP_MS
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.delete
import com.woka.feature.editor.tools.trim.duplicate
import com.woka.feature.editor.tools.trim.move
import com.woka.feature.editor.tools.trim.split
import com.woka.feature.editor.tools.trim.trimEnd
import com.woka.feature.editor.tools.trim.trimStart
import com.woka.feature.editor.tools.trim.wholeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The strip's edits as list functions (ME-210, ME-212…ME-214) — and above all the cases
 * where each must return the list **unchanged**, because the strip reads "unchanged" as
 * "push no history entry".
 */
class ClipEditsTest {

    private val whole = wholeSource(40_000L)

    @Test
    fun `two splits and a delete cut the middle out`() {
        val cut = whole.split(8_000L, "b").split(22_000L, "c")
        assertEquals(listOf(0L to 8_000L, 8_000L to 22_000L, 22_000L to 40_000L), cut.map { it.fromMs to it.toMs })
        val gone = cut.delete("b")
        assertEquals(listOf("source", "c"), gone.map { it.id })
        assertEquals(26_000L, gone.sumOf { it.lengthMs }, "the hole closed")
    }

    @Test
    fun `a split keeps the left half's id, so the selection stays put`() {
        val cut = whole.split(10_000L, "new")
        assertEquals("source", cut[0].id)
        assertEquals("new", cut[1].id)
    }

    @Test
    fun `a split too close to a join changes nothing`() {
        val cut = whole.split(8_000L, "b")
        assertSame(cut, cut.split(8_000L + MIN_CLIP_MS - 1, "x"))
        assertSame(cut, cut.split(8_000L, "x"), "a split exactly on a join")
    }

    @Test
    fun `the last clip is never deleted`() {
        assertSame(whole, whole.delete("source"))
    }

    @Test
    fun `a duplicate lands beside its original`() {
        val cut = whole.split(10_000L, "b").duplicate("source", "copy")
        assertEquals(listOf("source", "copy", "b"), cut.map { it.id })
        assertEquals(cut[0].fromMs to cut[0].toMs, cut[1].fromMs to cut[1].toMs)
    }

    @Test
    fun `a move re-inserts among the others and to the same slot is no change`() {
        val cut = whole.split(10_000L, "b").split(20_000L, "c")
        assertEquals(listOf("b", "c", "source"), cut.move("source", 2).map { it.id })
        assertEquals(listOf("c", "source", "b"), cut.move("c", 0).map { it.id })
        assertEquals(cut, cut.move("b", 1))
    }

    @Test
    fun `the ends trim inside the source and never past each other`() {
        assertEquals(2_500L, whole.trimStart("source", 2_500L).single().fromMs)
        assertEquals(0L, whole.trimStart("source", -900L).single().fromMs)
        assertEquals(40_000L - MIN_CLIP_MS, whole.trimStart("source", 41_000L).single().fromMs)
        assertEquals(40_000L, whole.trimEnd("source", 55_000L, 40_000L).single().toMs)
        assertEquals(MIN_CLIP_MS, whole.trimEnd("source", 0L, 40_000L).single().toMs)
    }
}
