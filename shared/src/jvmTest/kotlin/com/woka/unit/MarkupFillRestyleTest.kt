package com.woka.unit

import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.MarkupTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Where the `Fill` row is allowed to land (ME-146).
 *
 * Its own file because `MarkupRestyleTest` is at the size mandate, and the seam holds: that
 * one is about the two size rows routing by the selected shape's class, this one is about a
 * row that has **one** class it applies to at all. A plate on an arrow is not a smaller
 * mistake than a stroke width on a caption — it is a history entry for a picture that did
 * not change.
 */
class MarkupFillRestyleTest {

    private fun arrow(id: String) =
        MarkupShape.Arrow(id, 0xFFFF9F0AL, 6f, MarkupPoint(0f, 0f), MarkupPoint(50f, 50f))

    private fun text(id: String, fill: Long = 0L) =
        MarkupShape.Text(id, 0xFFFF9F0AL, 64f, MarkupPoint(10f, 10f), "Woka", fill = fill)

    private fun params(vararg shapes: MarkupShape) =
        MarkupTool.defaults().with(MarkupTool.Shapes, shapes.toList())

    @Test
    fun `choosing a fill with a caption selected plates that caption`() {
        val set = params(text("t"), text("u")).with(MarkupTool.Fill, 0xFF000000L)

        val shapes = requireNotNull(MarkupTool.restyled(MarkupTool.Fill, set, "t"))[MarkupTool.Shapes]

        assertEquals(0xFF000000L, assertIs<MarkupShape.Text>(shapes.first { it.id == "t" }).fill)
        assertEquals(0L, assertIs<MarkupShape.Text>(shapes.first { it.id == "u" }).fill)
    }

    /**
     * The plate is not the pen's `color`, and choosing one must not move the other — the
     * two are picked separately against the same photograph, which is why the shape carries
     * both.
     */
    @Test
    fun `plating a caption leaves its type colour and its size alone`() {
        val set = params(text("t")).with(MarkupTool.Fill, 0xFF0A84FFL)

        val shape = assertIs<MarkupShape.Text>(
            requireNotNull(MarkupTool.restyled(MarkupTool.Fill, set, "t"))[MarkupTool.Shapes].single()
        )

        assertEquals(0xFFFF9F0AL, shape.color, "the plate took the type's colour with it")
        assertEquals(64f, shape.width)
    }

    @Test
    fun `choosing a fill with a stroke selected restyles nothing`() {
        val set = params(arrow("a")).with(MarkupTool.Fill, 0xFF000000L)

        assertNull(MarkupTool.restyled(MarkupTool.Fill, set, "a"), "an arrow was given a background")
    }

    /** The corner row is the fill row's twin, and routes the same way. */
    @Test
    fun `the corner row rounds a caption and leaves a stroke alone`() {
        val onText = params(text("t")).with(MarkupTool.Corner, 60f)
        val onArrow = params(arrow("a")).with(MarkupTool.Corner, 60f)

        val shape = assertIs<MarkupShape.Text>(
            requireNotNull(MarkupTool.restyled(MarkupTool.Corner, onText, "t"))[MarkupTool.Shapes]
                .single()
        )

        assertEquals(60f, shape.corner)
        assertNull(MarkupTool.restyled(MarkupTool.Corner, onArrow, "a"), "an arrow was rounded")
    }

    /** The first swatch is `0L`, and writing it has to *remove* a plate rather than fail. */
    @Test
    fun `the none swatch clears a plate a caption already had`() {
        val set = params(text("t", fill = 0xFF000000L)).with(MarkupTool.Fill, 0L)

        val shape = assertIs<MarkupShape.Text>(
            requireNotNull(MarkupTool.restyled(MarkupTool.Fill, set, "t"))[MarkupTool.Shapes].single()
        )

        assertEquals(0L, shape.fill)
    }
}
