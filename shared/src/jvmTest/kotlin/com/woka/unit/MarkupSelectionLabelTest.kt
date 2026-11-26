package com.woka.unit

import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.MarkupTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What a selected Markup shape is called in a film's scope line (RD-302) — its bar's name. */
class MarkupSelectionLabelTest {

    private val arrow = MarkupShape.Arrow("a1", 0xFFFF9F0AL, 6f, MarkupPoint(0f, 0f), MarkupPoint(50f, 50f))
    private val params = ParamSet().with(MarkupTool.Shapes, listOf(arrow))

    @Test
    fun `the selected shape is named by kind`() {
        assertEquals("Arrow", MarkupTool.selectionLabel(params, "a1"))
    }

    @Test
    fun `nothing selected, or a shape no longer there, names nothing`() {
        assertNull(MarkupTool.selectionLabel(params, null))
        assertNull(MarkupTool.selectionLabel(params, "gone"))
    }
}
