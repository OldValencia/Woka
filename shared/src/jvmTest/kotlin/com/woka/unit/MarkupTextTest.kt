package com.woka.unit

import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.hitTest
import com.woka.feature.editor.tools.markup.movedBy
import com.woka.feature.editor.tools.markup.textBounds
import com.woka.feature.editor.tools.markup.textLayoutOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a text shape measures, and therefore where it can be grabbed (ME-122).
 *
 * **The size is a count of source pixels**, not of anything the screen knows about, and
 * every assertion here is a consequence of that: bounds that grow with the size, bounds
 * that grow with the string, and a shape whose top-left is exactly where it was placed.
 * A measurer built against the display's density would fail all three on a HiDPI machine
 * and pass on a plain one, which is why they are asserted rather than looked at.
 */
class MarkupTextTest {

    private fun text(
        string: String = "Woka",
        size: Float = 64f,
        at: MarkupPoint = MarkupPoint(120f, 80f)
    ) = MarkupShape.Text("t", 0xFFFF9F0AL, size, at, string)

    @Test
    fun `the bounds start at the point the shape was placed`() {
        val bounds = textBounds(text(at = MarkupPoint(120f, 80f)))

        assertEquals(120f, bounds.left)
        assertEquals(80f, bounds.top)
    }

    @Test
    fun `the bounds grow with the size`() {
        val small = textBounds(text(size = 32f))
        val large = textBounds(text(size = 96f))

        assertTrue(large.width > small.width, "a larger size measured no wider")
        assertTrue(large.height > small.height, "a larger size measured no taller")
    }

    /**
     * Three times the size is close to three times the width — not exactly, because
     * hinting and rounding move a glyph advance by a fraction — and the tolerance is what
     * separates "the size is a scale factor" from "the size is being ignored".
     */
    @Test
    fun `three times the size is three times the width, within rounding`() {
        val one = textBounds(text(size = 32f)).width
        val three = textBounds(text(size = 96f)).width

        assertTrue(three in (one * 2.8f)..(one * 3.2f), "96 px measured ${three}, 32 px measured $one")
    }

    @Test
    fun `the bounds grow with the string`() {
        val short = textBounds(text("W"))
        val long = textBounds(text("Wokawokawoka"))

        assertTrue(long.width > short.width, "a longer string measured no wider")
        // One line, so it does not get taller: the field ME-145 mounts commits on Enter.
        assertEquals(short.height, long.height)
    }

    /**
     * A shape nobody has typed into measures nothing and is nothing — the rule that keeps
     * an accidental placement from leaving an invisible mark in the document.
     */
    @Test
    fun `a blank string has no layout and no bounds`() {
        assertNull(textLayoutOf(text("")))
        assertNull(textLayoutOf(text("   ")))
        assertTrue(textBounds(text("")).isEmpty)
    }

    @Test
    fun `a blank shape cannot be hit, at the origin or anywhere else`() {
        val blank = text(string = "", at = MarkupPoint(0f, 0f))

        assertFalse(blank.hitTest(0f, 0f, tolerance = 12f), "an empty shape was grabbable")
        assertFalse(blank.hitTest(120f, 80f, tolerance = 12f))
    }

    /**
     * **Filled, not outlined.** Type is a solid mark and the space between its letters
     * belongs to it, which is the one place markup's hit testing differs from a drawn box.
     */
    @Test
    fun `a point inside the bounds hits and one beyond the tolerance does not`() {
        val shape = text()
        val bounds = textBounds(shape)

        assertTrue(shape.hitTest(bounds.center.x, bounds.center.y, tolerance = 4f))
        assertTrue(shape.hitTest(bounds.left + 1f, bounds.top + 1f, tolerance = 4f))
        assertFalse(shape.hitTest(bounds.right + 20f, bounds.center.y, tolerance = 4f))
        assertFalse(shape.hitTest(bounds.center.x, bounds.top - 20f, tolerance = 4f))
    }

    @Test
    fun `moving translates the bounds by exactly the delta`() {
        val shape = text()
        val before = textBounds(shape)

        val moved = textBounds(shape.movedBy(30f, -12f) as MarkupShape.Text)

        assertEquals(before.left + 30f, moved.left)
        assertEquals(before.top - 12f, moved.top)
        assertEquals(before.size, moved.size, "a move resized the shape")
    }
}
