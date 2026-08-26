package com.woka.unit

import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorToolScope
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.MarkupTextEdit
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.liveEdit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The caption the caret is measured from (reported by hand 2026-08-27).
 *
 * **`MarkupTextEdit.shape` is deliberately frozen** — `commitTextEdit` compares against it
 * to tell an emptied caption from an abandoned caret — so the field cannot draw from it.
 * Choosing a size while the caret is open restyles the entry in the *list*, and a caret
 * still measured from the opening shape stayed the height it started at while the text
 * under it grew or shrank. `liveEdit` is the one line between the two, and this is what it
 * has to do.
 */
class MarkupLiveEditTest {

    private val opened =
        MarkupShape.Text("t", 0xFFFF9F0AL, 140f, MarkupPoint(10f, 10f), "Woka")

    private fun scopeOver(vararg shapes: MarkupShape): EditorToolScope {
        val params = MarkupTool.defaults().with(MarkupTool.Shapes, shapes.toList())
        return FrozenScope(EditDocument(ops = listOf(ToolOp(MarkupTool.manifest.id, params))))
    }

    @Test
    fun `the caret takes the size the document now has`() {
        val resized = opened.copy(width = 40f)
        val edit = scopeOver(resized).liveEdit(MarkupTextEdit(opened, "Woka", existed = false))

        assertEquals(40f, edit.shape.width, "the caret stayed at the size it opened with")
        // The string being typed is the field's, not the document's, and survives untouched.
        assertEquals("Woka", edit.text)
    }

    @Test
    fun `the caret takes the colour the document now has`() {
        val recoloured = opened.copy(color = 0xFF0A84FFL)
        val edit = scopeOver(recoloured).liveEdit(MarkupTextEdit(opened, "Woka", existed = true))

        assertEquals(0xFF0A84FFL, edit.shape.color)
        assertEquals(true, edit.existed, "the commit's own record of the caption was rewritten")
    }

    /**
     * The frame between an undo taking the caption away and the edit closing: there is
     * nothing to measure from, and the opening shape is the only honest answer.
     */
    @Test
    fun `a caption the document no longer holds falls back to the opening shape`() {
        val edit = scopeOver().liveEdit(MarkupTextEdit(opened, "Woka", existed = true))

        assertEquals(opened, edit.shape)
    }
}

/**
 * A scope over one document and nothing else — the edit lookup reads the document and
 * touches none of the verbs, so the rest throws rather than pretending to work.
 */
private class FrozenScope(override val document: EditDocument) : EditorToolScope {
    override val sourceSize = IntSize(1000, 800)
    override val canvasToSource = Matrix()
    override val sourceToCanvas = Matrix()
    override val selectedId: String? = null
    override fun select(id: String?) = error("not part of this scenario")
    override fun preview(op: ToolOp) = error("not part of this scenario")
    override fun commit() = error("not part of this scenario")
    override fun cancel() = error("not part of this scenario")
    override fun captureKeys(captured: Boolean) = error("not part of this scenario")
}
