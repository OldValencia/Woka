package com.woka.unit

import com.woka.feature.editor.shell.hsvOf
import com.woka.feature.editor.shell.isSwatch
import com.woka.feature.editor.shell.packHsv
import com.woka.feature.editor.tools.markup.MarkupGesture
import com.woka.feature.editor.tools.markup.MarkupKind
import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.extendedTo
import com.woka.feature.editor.tools.markup.markupGestureAt
import com.woka.feature.editor.tools.markup.newMarkupShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What a press means, and what a drag does to a shape (ME-114) — tested without a
 * pointer, which is the whole reason `MarkupGesture` is a separate pure file.
 *
 * The colour arithmetic the custom picker is drawn from rides along here: it is the same
 * kind of claim (a pure conversion the UI depends on), and a file of its own for four
 * assertions would be a file nobody opens.
 */
class MarkupGestureTest {

    private val box = MarkupShape.Rectangle(
        id = "box",
        color = 0L,
        width = 4f,
        from = MarkupPoint(0f, 0f),
        to = MarkupPoint(100f, 60f)
    )

    private val arrow = MarkupShape.Arrow(
        id = "arrow",
        color = 0L,
        width = 4f,
        from = MarkupPoint(200f, 200f),
        to = MarkupPoint(300f, 200f)
    )

    /** A handle sits on the outline it belongs to, so it has to win over that outline. */
    @Test
    fun `the selected shape's handle wins over its own body`() {
        val at = markupGestureAt(listOf(box), selected = box, x = 100f, y = 60f, grab = 6f)

        assertEquals(MarkupGesture.Resize("box", handle = 2), at)
    }

    @Test
    fun `a press on a shape that is not selected moves it`() {
        val at = markupGestureAt(listOf(box, arrow), selected = null, x = 250f, y = 200f, grab = 6f)

        assertEquals(MarkupGesture.Move("arrow"), at)
    }

    /** Drawn last is drawn on top, so it is what a press must grab. */
    @Test
    fun `overlapping shapes are grabbed topmost first`() {
        val under = box.copy(id = "under")
        val over = box.copy(id = "over")
        val at = markupGestureAt(listOf(under, over), selected = null, x = 0f, y = 30f, grab = 6f)

        assertEquals(MarkupGesture.Move("over"), at)
    }

    @Test
    fun `a press on empty picture is a draw`() {
        assertEquals(
            MarkupGesture.Draw,
            markupGestureAt(listOf(box), selected = box, x = 400f, y = 400f, grab = 6f)
        )
    }

    @Test
    fun `a new shape carries the pen it was drawn with`() {
        val fresh = newMarkupShape(
            MarkupKind.Ellipse, 0xFF00FF00L, 9f, MarkupPoint(5f, 6f),
            wrap = 0f, fill = 0L, corner = 25f, faces = emptySet()
        )

        val oval = assertIs<MarkupShape.Ellipse>(fresh)
        assertEquals(0xFF00FF00L, oval.color)
        assertEquals(9f, oval.width)
        // Zero-sized until the drag moves: `stage()` skips it, so a click leaves nothing.
        assertEquals(oval.from, oval.to)
        assertTrue(oval.id.isNotEmpty(), "a new shape has no id to select it by")
    }

    /**
     * A freehand stroke **accumulates**, and drops a point nearer than a source pixel to
     * the last: a 6000 px photograph at 60 fps otherwise writes a thousand points per
     * stroke into the user's file.
     */
    @Test
    fun `a freehand stroke accumulates and thins`() {
        val start = newMarkupShape(
            MarkupKind.Freehand, 0L, 4f, MarkupPoint(0f, 0f),
            wrap = 0f, fill = 0L, corner = 25f, faces = emptySet()
        )

        val far = assertIs<MarkupShape.Freehand>(start.extendedTo(MarkupPoint(10f, 0f)))
        assertEquals(2, far.points.size)

        val near = assertIs<MarkupShape.Freehand>(far.extendedTo(MarkupPoint(10.4f, 0f)))
        assertEquals(2, near.points.size, "a sub-pixel step was stored")
    }

    /** Every other shape replaces its far end instead. */
    @Test
    fun `extending a box moves only its far corner`() {
        val stretched = assertIs<MarkupShape.Rectangle>(box.extendedTo(MarkupPoint(7f, 8f)))

        assertEquals(MarkupPoint(0f, 0f), stretched.from)
        assertEquals(MarkupPoint(7f, 8f), stretched.to)
    }

    @Test
    fun `a colour round-trips through hue, saturation and brightness`() {
        listOf(0xFFFF9F0AL, 0xFF0A84FFL, 0xFF30D158L, 0xFFFFFFFFL, 0xFF000000L).forEach { argb ->
            val hsv = hsvOf(argb)
            assertEquals(argb, packHsv(hsv.hue, hsv.saturation, hsv.value), "$argb did not survive")
        }
    }

    /**
     * A grey has no hue — every channel is equal and the formula's denominator is zero —
     * so it reports 0f at zero saturation rather than dividing.
     */
    @Test
    fun `a grey reports no hue and packs back to itself`() {
        val hsv = hsvOf(0xFF808080L)

        assertEquals(0f, hsv.hue)
        assertEquals(0f, hsv.saturation)
        assertEquals(0xFF808080L, packHsv(hsv.hue, hsv.saturation, hsv.value))
    }

    /** What decides between a ring on a swatch and a ring on the custom circle. */
    @Test
    fun `a picked colour outside the offered set is not a swatch`() {
        val swatches = listOf(0xFFFF9F0AL, 0xFF0A84FFL)

        assertTrue(0xFFFF9F0AL.isSwatch(swatches))
        assertFalse(0xFF123456L.isSwatch(swatches))
    }
}
