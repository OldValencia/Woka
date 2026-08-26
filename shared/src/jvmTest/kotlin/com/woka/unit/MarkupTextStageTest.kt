package com.woka.unit

import com.woka.feature.editor.engine.image.RasterStage
import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.MarkupTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * What the fifth shape does to the tool that owns it (ME-122).
 *
 * Its own file rather than more of `MarkupToolTest`, which is at the size mandate: that
 * one asserts the claim ME-113 was written for — a list is *one* stage — and this one
 * asserts the exception ME-122 introduces, which is that a shape can be in the list and
 * still be nothing.
 */
class MarkupTextStageTest {

    private fun text(id: String, string: String, size: Float = 64f) =
        MarkupShape.Text(id, 0xFFFF9F0AL, size, MarkupPoint(20f, 20f), string)

    private fun withShapes(vararg shapes: MarkupShape) =
        MarkupTool.defaults().with(MarkupTool.Shapes, shapes.toList())

    /**
     * The pen carries a second size row, and the order it is declared in is the order the
     * inspector draws it: after the stroke width, before the payload the shell renders
     * nothing for.
     */
    @Test
    fun `the pen declares a text size beside the stroke width`() {
        assertEquals(
            listOf(
                MarkupTool.Kind,
                MarkupTool.Color,
                MarkupTool.Width,
                MarkupTool.TextSize,
                MarkupTool.Shapes
            ),
            MarkupTool.params
        )
    }

    @Test
    fun `a text shape composes a paint stage like any other`() {
        assertIs<RasterStage.Paint>(assertNotNull(MarkupTool.stage(withShapes(text("t", "Woka")))))
    }

    /**
     * **A shape nobody typed into is not work.** A stage that paints no pixel is still a
     * stage the fold walks and sorts on every frame, so a list of nothing has to answer
     * null exactly as an empty list does — which is also what keeps a document that has
     * been opened and closed identical to the one that was opened.
     */
    @Test
    fun `a list of only blank text composes no stage`() {
        assertNull(MarkupTool.stage(withShapes(text("t", ""), text("u", "   "))))
    }

    /** One blank among real shapes drops the blank and leaves the rest drawn. */
    @Test
    fun `a blank text beside an arrow leaves the arrow drawn`() {
        val arrow = MarkupShape.Arrow("a", 0L, 6f, MarkupPoint(0f, 0f), MarkupPoint(50f, 50f))

        assertIs<RasterStage.Paint>(assertNotNull(MarkupTool.stage(withShapes(arrow, text("t", "")))))
    }
}
