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
