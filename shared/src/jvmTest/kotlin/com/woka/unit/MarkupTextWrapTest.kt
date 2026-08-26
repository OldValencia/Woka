package com.woka.unit

import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.textBounds
import com.woka.feature.editor.tools.markup.textLayoutOf
import com.woka.feature.editor.tools.markup.wrapAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two fields ME-146 put on a caption: the width it wraps against and the plate behind
 * it.
 *
 * Its own file rather than more of `MarkupTextTest`, which is at the size mandate — and
 * the seam is real: that one asserts a caption is measured in **source** pixels, this one
 * asserts what the wrap width and the plate then do to that measurement.
 */
class MarkupTextWrapTest {

    private val sentence = "Woka wraps a caption at the picture's own width"

    private fun text(
        string: String = sentence,
        wrap: Float = 0f,
        fill: Long = 0L
    ) = MarkupShape.Text("t", 0xFFFF9F0AL, 48f, MarkupPoint(20f, 30f), string, wrap, fill)

    @Test
    fun `a string wider than its wrap breaks onto more lines and stays inside it`() {
        val wrapped = text(wrap = 300f)

        val layout = requireNotNull(textLayoutOf(wrapped))
        assertTrue(layout.lineCount > 1, "a wrap of 300 px laid ${layout.lineCount} line out")
        assertTrue(textBounds(wrapped).width <= 300f, "the layout ran past its own wrap width")
    }

    /**
     * **Zero is the ME-122 document**, and it has to go on meaning what it meant: one line,
     * however long, measured against nothing.
     */
    @Test
    fun `a wrap of zero is unconstrained and still one line`() {
        val unwrapped = text(wrap = 0f)

        val layout = requireNotNull(textLayoutOf(unwrapped))
        assertEquals(1, layout.lineCount)
        assertTrue(textBounds(unwrapped).width > 300f, "the unwrapped line measured narrow")
    }

    @Test
    fun `the same string wraps taller and narrower than it does unwrapped`() {
        val wide = textBounds(text(wrap = 0f))
        val narrow = textBounds(text(wrap = 300f))

        assertTrue(narrow.width < wide.width, "wrapping did not narrow the block")
        assertTrue(narrow.height > wide.height, "wrapping did not add a line's height")
    }

    /**
     * **The box is the longest line, not the wrap width** (measured 2026-08-27).
     * `TextLayoutResult.size.width` reports the constraint it was given, so a block wrapped
     * at 300 px whose longest line is 266 px measured 300 — and the plate ran past the type
     * by 34 px, all of it on the right, which is what "the text is not centred in its own
     * background" looked like.
     */
    @Test
    fun `the box hugs the longest line rather than the width it wrapped against`() {
        val wrapped = text(wrap = 300f)
        val layout = requireNotNull(textLayoutOf(wrapped))
        val longest = (0 until layout.lineCount).maxOf { layout.getLineRight(it) }

        assertTrue(longest < 300f, "the fixture no longer has slack to measure")
        assertEquals(longest, textBounds(wrapped).width, "the box took the constraint's width")
    }

    /**
     * The plate is part of the shape's box, so a caption on a background is grabbed by the
     * background the eye sees — and a caption without one keeps exactly the box ME-122
     * measured, which is what makes this two assertions rather than one.
     */
    @Test
    fun `a plate outsets the bounds and no plate leaves them alone`() {
        val bare = textBounds(text())
        val plated = textBounds(text(fill = 0xFF000000L))

        assertTrue(plated.width > bare.width, "an opaque plate did not outset the bounds")
        assertEquals(bare.left - plated.left, bare.top - plated.top, "the outset is not square")
    }

    /**
     * `0L` is transparent black, not a colour — the value the `Fill` row's first swatch
     * writes, and the one every document written before ME-146 decodes to.
     */
    @Test
    fun `a fill with no alpha is no plate`() {
        assertEquals(textBounds(text()), textBounds(text(fill = 0x00FF3B30L)))
    }

    @Test
    fun `an inherited wrap runs from the caption to the picture's right edge`() {
        assertEquals(4000f, wrapAt(sourceWidth = 4000, x = 0f))
        assertEquals(3000f, wrapAt(sourceWidth = 4000, x = 1000f))
    }

    /** A caret placed near the right edge inherits a quarter of the picture, not a column. */
    @Test
    fun `an inherited wrap is floored at a quarter of the picture`() {
        assertEquals(1000f, wrapAt(sourceWidth = 4000, x = 3900f))
    }
}
