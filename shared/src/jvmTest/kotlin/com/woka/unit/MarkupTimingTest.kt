package com.woka.unit

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.TimeWindow
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.tools.markup.MarkupAppearsParam
import com.woka.feature.editor.tools.markup.MarkupFadeOutParam
import com.woka.feature.editor.tools.markup.MarkupFromPlayHeadParam
import com.woka.feature.editor.tools.markup.MarkupHoldsParam
import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.MarkupTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A markup shape's time on a film (ME-263): the rows, the write into its window, the read back. */
class MarkupTimingTest {

    private val box = MarkupShape.Rectangle("box", 0xFFFF0000, 4f, MarkupPoint(0f, 0f), MarkupPoint(10f, 10f))

    private val drawn = ParamSet().with(MarkupTool.Shapes, listOf(box))

    private val size = IntSize(640, 360)

    private fun keys(params: ParamSet, selected: String?, timelineMs: Long?) =
        MarkupTool.rows(params, size, selected, timelineMs).map { it.key }

    @Test
    fun `the timing rows show only for a film with a shape selected`() {
        val timing = MarkupAppearsParam.key
        assertTrue(timing in keys(drawn, "box", 10_000L))
        assertFalse(timing in keys(drawn, null, 10_000L), "nothing selected")
        assertFalse(timing in keys(drawn, "box", null), "a still")
        assertFalse(timing in MarkupTool.rows(drawn, size).map { it.key }, "the plain rows")
    }

    @Test
    fun `a first timing write starts from the whole film`() {
        val written = drawn.with(MarkupFadeOutParam, 0.5f)
        val restyled = assertNotNull(MarkupTool.restyled(MarkupFadeOutParam, written, "box", 8_000L))
        assertEquals(TimeWindow(0L, 8_000L, fadeOutMs = 500L), restyled[MarkupTool.Windows]["box"])
    }

    @Test
    fun `the panel reads the selected shape's window back`() {
        val timed = drawn.with(MarkupTool.Windows, mapOf("box" to TimeWindow(5_000L, 3_000L, fadeOutMs = 500L)))
        val shown = MarkupTool.displayed(timed, "box")
        assertEquals(5f, shown[MarkupAppearsParam])
        assertEquals(3f, shown[MarkupHoldsParam])
        assertEquals(0.5f, shown[MarkupFadeOutParam])
    }

    @Test
    fun `appear at play head keeps every shape`() {
        val document = EditDocument(listOf(ToolOp(MarkupTool.manifest.id, drawn)))
        val blank = MarkupTool.defaults(size)
        val moved = assertNotNull(MarkupTool.atPlayHead(MarkupFromPlayHeadParam, blank, 2_500L, document))
        val restyled = assertNotNull(MarkupTool.restyled(MarkupFromPlayHeadParam, moved, "box", 8_000L))
        assertEquals(listOf(box), restyled[MarkupTool.Shapes])
        assertEquals(2_500L, restyled[MarkupTool.Windows]["box"]?.atMs)
    }
}
