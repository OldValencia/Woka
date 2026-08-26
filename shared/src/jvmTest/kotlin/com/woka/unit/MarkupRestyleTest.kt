package com.woka.unit

import com.woka.feature.editor.shell.hsvOf
import com.woka.feature.editor.shell.packHsv
import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.MarkupTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Restyling the selected shape from the inspector (ME-114, asked for 2026-08-24).
 *
 * `Color` and `Width` are the pen for the *next* shape, because every shape stores its own
 * style — right while nothing is selected, and plainly wrong the moment something is.
 * `CanvasTool.restyled` is the hook that resolves that, and this is what it must do.
 */
class MarkupRestyleTest {

    private fun arrow(id: String, color: Long = 0xFFFF9F0AL, width: Float = 6f) =
        MarkupShape.Arrow(id, color, width, MarkupPoint(0f, 0f), MarkupPoint(50f, 50f))

    private fun text(id: String, color: Long = 0xFFFF9F0AL, size: Float = 64f) =
        MarkupShape.Text(id, color, size, MarkupPoint(10f, 10f), "Woka")

    private fun params(vararg shapes: MarkupShape) =
        MarkupTool.defaults().with(MarkupTool.Shapes, shapes.toList())

    @Test
    fun `choosing a colour with a shape selected restyles that shape and nothing else`() {
        val set = params(arrow("a"), arrow("b")).with(MarkupTool.Color, 0xFF0A84FFL)

        val restyled = MarkupTool.restyled(MarkupTool.Color, set, selectedId = "b")
        val shapes = requireNotNull(restyled)[MarkupTool.Shapes]

        assertEquals(0xFF0A84FFL, shapes.first { it.id == "b" }.color, "the selected shape kept its colour")
        assertEquals(0xFFFF9F0AL, shapes.first { it.id == "a" }.color, "an unselected shape was restyled")
        // The pen moves too: the swatch has to be both what is selected and what the next
        // shape is drawn with, or the row would mean two different things.
        assertEquals(0xFF0A84FFL, restyled[MarkupTool.Color])
    }

    @Test
    fun `choosing a width with a shape selected restyles that shape`() {
        val set = params(arrow("a")).with(MarkupTool.Width, 20f)

        val shapes = requireNotNull(MarkupTool.restyled(MarkupTool.Width, set, "a"))[MarkupTool.Shapes]

        assertEquals(20f, shapes.single().width)
        // Geometry untouched: a restyle is not a move.
        assertEquals(MarkupPoint(50f, 50f), (shapes.single() as MarkupShape.Arrow).to)
    }

    /** Nothing selected is the pen-only case, and it must not rewrite the list at all. */
    @Test
    fun `nothing selected restyles nothing`() {
        val set = params(arrow("a")).with(MarkupTool.Color, 0xFF0A84FFL)

        assertNull(MarkupTool.restyled(MarkupTool.Color, set, selectedId = null))
    }

    /**
     * A selection can outlive the shape it named — an undo takes the shape away and leaves
     * the id behind — and restyling a shape that is gone must be a no-op rather than a
     * rewrite of the list it is not in.
     */
    @Test
    fun `a selection naming a shape the document no longer holds restyles nothing`() {
        val set = params(arrow("a")).with(MarkupTool.Color, 0xFF0A84FFL)

        assertNull(MarkupTool.restyled(MarkupTool.Color, set, selectedId = "gone"))
    }

    /**
     * **The two size rows route by the selected shape's class** (ME-122). Both write the
     * same field — a shape carries one number and a `Text` calls it a font size — so what
     * the routing decides is which row is allowed to reach it. Getting this wrong is not a
     * crash: it is a stroke slider silently resizing a caption to 6 px.
     */
    @Test
    fun `the text size row restyles a text shape and the width row does not`() {
        val set = params(text("t")).with(MarkupTool.TextSize, 120f).with(MarkupTool.Width, 6f)

        val restyled = requireNotNull(MarkupTool.restyled(MarkupTool.TextSize, set, "t"))
        assertEquals(120f, restyled[MarkupTool.Shapes].single().width)

        assertNull(MarkupTool.restyled(MarkupTool.Width, set, "t"), "the stroke row resized type")
    }

    @Test
    fun `the width row restyles a stroke and the text size row does not`() {
        val set = params(arrow("a")).with(MarkupTool.Width, 20f).with(MarkupTool.TextSize, 120f)

        val restyled = requireNotNull(MarkupTool.restyled(MarkupTool.Width, set, "a"))
        assertEquals(20f, restyled[MarkupTool.Shapes].single().width)

        assertNull(MarkupTool.restyled(MarkupTool.TextSize, set, "a"), "the type row thickened a stroke")
    }

    /**
     * Colour reaches both, and carries the size row that belongs to the shape it lands on
     * — so recolouring a caption must not also resize it to the stroke width sitting in
     * the row above.
     */
    @Test
    fun `recolouring a text shape keeps its own size rather than the stroke width`() {
        val set = params(text("t", size = 64f))
            .with(MarkupTool.Color, 0xFF0A84FFL)
            .with(MarkupTool.TextSize, 64f)
            .with(MarkupTool.Width, 6f)

        val shape = requireNotNull(MarkupTool.restyled(MarkupTool.Color, set, "t"))[MarkupTool.Shapes]

        assertEquals(0xFF0A84FFL, shape.single().color)
        assertEquals(64f, shape.single().width, "the caption took the stroke slider's number")
    }

    /** `Kind` is not a restyle: a drawn rectangle cannot become an arrow. */
    @Test
    fun `the shape chooser is not a restyle`() {
        val set = params(arrow("a"))

        assertNull(MarkupTool.restyled(MarkupTool.Kind, set, selectedId = "a"))
    }

    /**
     * **The picker's reset-to-red, as a unit test.** Black, white and every grey have no
     * hue — all three channels are equal — so a colour cannot carry the three numbers a
     * picker is arranged in. Deriving the panel's state from the stored colour each frame
     * therefore threw the hue away in the pad's dark and pale corners, and the strip
     * snapped to 0°. The panel holds its own HSV; this is the arithmetic that forced it.
     */
    @Test
    fun `a colour cannot carry the hue a picker needs`() {
        // Blue, taken to black: the hue is gone from the packed value, not merely rounded.
        val black = packHsv(hue = 220f, saturation = 0.9f, value = 0f)
        assertEquals(0xFF000000L, black)
        assertEquals(0f, hsvOf(black).hue, "a black that remembered its hue would be a different bug")

        // And to white, which loses it the other way.
        val white = packHsv(hue = 220f, saturation = 0f, value = 1f)
        assertEquals(0xFFFFFFFFL, white)
        assertEquals(0f, hsvOf(white).hue)
    }
}
