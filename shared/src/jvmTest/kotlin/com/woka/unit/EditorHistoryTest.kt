package com.woka.unit

import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorHistory
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A document identifiable by its single op's id, so ordering assertions read. */
private fun document(marker: String) =
    EditDocument(ops = listOf(ToolOp(marker, ParamSet())))

private fun op(marker: String) = ToolOp(marker, ParamSet())

/** The **last** op names the entry: `startingAt` builds prefixes, not single ops. */
private fun EditorHistory.markers() = entries.map { it.ops.lastOrNull()?.toolId ?: "start" }

class EditorHistoryTest {

    @Test
    fun `a fresh history holds the opening document and can do neither`() {
        val history = EditorHistory()
        assertTrue(history.current.isEmpty)
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun `undo and redo walk the cursor without losing entries`() {
        val history = EditorHistory().push(document("a")).push(document("b"))
        assertEquals("b", history.current.ops.single().toolId)

        val undone = history.undo()
        assertEquals("a", undone.current.ops.single().toolId)
        assertTrue(undone.canRedo)
        assertEquals(3, undone.entries.size)

        assertEquals("b", undone.redo().current.ops.single().toolId)
    }

    @Test
    fun `undo at the start and redo at the end are no-ops`() {
        val start = EditorHistory()
        assertEquals(start, start.undo())
        val end = start.push(document("a"))
        assertEquals(end, end.redo())
    }

    @Test
    fun `pushing after an undo truncates the redo tail`() {
        val history = EditorHistory()
            .push(document("a"))
            .push(document("b"))
            .undo()
            .push(document("c"))

        assertEquals(listOf("start", "a", "c"), history.markers())
        assertFalse(history.canRedo)
    }

    @Test
    fun `the cap holds at 50 and drops the oldest`() {
        val history = (1..60).fold(EditorHistory()) { acc, i -> acc.push(document("op$i")) }

        assertEquals(50, history.entries.size)
        // The newest survives and the cursor still points at it…
        assertEquals("op60", history.current.ops.single().toolId)
        assertEquals(49, history.index)
        // …and what fell off the front is the oldest, not the newest.
        assertEquals("op11", history.markers().first())
        assertFalse(history.markers().contains("start"))
    }

    /**
     * The file's recipe replayed as a history (ME-134): one entry per operation, the
     * cursor on the last, and the original photograph at the very beginning.
     */
    @Test
    fun `startingAt replays a carried document one operation at a time`() {
        val carried = EditDocument(ops = listOf(op("a"), op("b"), op("c")))

        val history = EditorHistory.startingAt(carried)

        assertEquals(listOf("start", "a", "b", "c"), history.markers())
        assertEquals(3, history.index)
        assertEquals(carried, history.current)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun `startingAt on an unedited file is a plain fresh history`() {
        val history = EditorHistory.startingAt(EditDocument())

        assertTrue(history.current.isEmpty)
        assertFalse(history.canUndo)
    }
}
