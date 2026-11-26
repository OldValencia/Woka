package com.woka.unit

import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.TimeWindow
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.video.BarPart
import com.woka.feature.editor.engine.video.laneRows
import com.woka.feature.editor.engine.video.markupBarsIn
import com.woka.feature.editor.engine.video.moveWindow
import com.woka.feature.editor.engine.video.withWindow
import com.woka.feature.editor.engine.video.withoutShape
import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.MarkupWindowsParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The Markup lane's model (RD-301): a bar per shape, rows for overlaps, and what a drag writes. */
class MarkupLaneRowsTest {

    private val caption = MarkupShape.Text("t", 0xFFFFFFFFL, 2f, MarkupPoint(0.5f, 0.8f), "Nowhere else to be.")
    private val box = MarkupShape.Rectangle("r", 0xFFFF0000L, 2f, MarkupPoint(0.1f, 0.1f), MarkupPoint(0.3f, 0.3f))

    private fun film(windows: Map<String, TimeWindow>) = EditDocument(
        listOf(
            ToolOp(
                MarkupTool.manifest.id,
                ParamSet().with(MarkupTool.Shapes, listOf(caption, box)).with(MarkupWindowsParam, windows)
            )
        )
    )

    @Test
    fun `a shape with no window spans the whole film and a text reads its words`() {
        val bars = markupBarsIn(film(mapOf("r" to TimeWindow(2_000L, 1_000L))), totalMs = 30_000L)
        assertEquals(TimeWindow(0L, 30_000L), bars.first { it.id == "t" }.window)
        assertEquals("Nowhere else to be.", bars.first { it.id == "t" }.label)
        assertEquals("Rectangle", bars.first { it.id == "r" }.label)
    }

    @Test
    fun `overlapping windows take a row each and a later one reuses a free row`() {
        val rows = laneRows(listOf(TimeWindow(0L, 5_000L), TimeWindow(2_000L, 5_000L), TimeWindow(6_000L, 1_000L)))
        assertEquals(listOf(0, 1, 0), rows)
    }

    @Test
    fun `a body moved stays inside the film and keeps its length`() {
        val (moved, snapped) = moveWindow(TimeWindow(1_000L, 4_000L), BarPart.Body, 30_000L, 10_000L, emptyList(), 100L)
        assertEquals(TimeWindow(6_000L, 4_000L), moved)
        assertNull(snapped)
    }

    @Test
    fun `an end snaps to the play head within reach and keeps a tenth of a second`() {
        val window = TimeWindow(1_000L, 4_000L)
        val (moved, snapped) = moveWindow(window, BarPart.End, -1_950L, 10_000L, listOf(3_000L), 100L)
        assertEquals(TimeWindow(1_000L, 2_000L), moved)
        assertEquals(3_000L, snapped)
        assertEquals(100L, moveWindow(window, BarPart.End, -9_000L, 10_000L, emptyList(), 100L).first.holdMs)
    }

    @Test
    fun `a start moved keeps the end where it was`() {
        val (moved, _) = moveWindow(TimeWindow(1_000L, 4_000L), BarPart.Start, 1_500L, 10_000L, emptyList(), -1L)
        assertEquals(TimeWindow(2_500L, 2_500L), moved)
    }

    @Test
    fun `a window is written beside the shapes and a removed shape takes its window with it`() {
        val op = film(emptyMap()).ops.single()
        val timed = withWindow(op, "t", TimeWindow(3_000L, 2_000L))
        assertEquals(TimeWindow(3_000L, 2_000L), timed.params[MarkupWindowsParam]["t"])
        val removed = withoutShape(timed, "t")
        assertEquals(listOf("r"), removed.params[MarkupTool.Shapes].map { it.id })
        assertNull(removed.params[MarkupWindowsParam]["t"])
    }
}
