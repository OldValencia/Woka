package com.woka.unit

import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.textBounds
import com.woka.feature.editor.tools.markup.textLayoutOf
import com.woka.feature.editor.tools.markup.withHandle
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Dragging a caption's corner (ME-148).
 *
 * **Every assertion here is about a ratio.** A corner scales the type, so `width`, `wrap`
 * and `at` move by one factor and the opposite corner does not move at all — which is what
 * separates this from resizing a rectangle, where two corners are stored and one is
 * written.
 */
class MarkupTextFrameTest {

    private fun caption(size: Float = 64f, wrap: Float = 400f) = MarkupShape.Text(
        id = "t",
        color = 0xFFFF9F0AL,
        width = size,
        at = MarkupPoint(120f, 80f),
        text = "Woka markup caption",
        wrap = wrap
    )

    /** Index into `frameHandles`: topLeft, topRight, bottomRight, bottomLeft. */
    private val topLeft = 0
    private val bottomRight = 2

    @Test
    fun `dragging the bottom-right corner twice as far doubles the size and the box`() {
        val before = caption()
        val box = textBounds(before)
        // Twice the diagonal from the top-left, which is the anchor for this handle.
        val target = box.topLeft + (box.bottomRight - box.topLeft) * 2f

        val after = assertIs<MarkupShape.Text>(before.withHandle(bottomRight, target.x, target.y))

        assertEquals(128f, after.width, 0.01f)
        assertEquals(800f, after.wrap, 0.01f)
        // **The anchor is the box's top-left, which is not `at`** (ME-152): the box starts
        // at the ascender and `at` at the top of the font's line, so a scale about the
        // corner moves `at` and pins the corner. The corner is what the user grabbed.
        assertEquals(box.topLeft, textBounds(after).topLeft)
    }

    /**
     * The anchor claim, from the other end: dragging the **top-left** must move `at`, and
     * what stays put is the far corner of the *bounds*. Asserted on the rect rather than
     * on `at`, because asserting on `at` would be asserting the opposite.
     */
    @Test
    fun `dragging the top-left corner leaves the bottom-right of the box where it was`() {
        val before = caption()
        val box = textBounds(before)
        val target = box.bottomRight + (box.topLeft - box.bottomRight) * 1.5f

        val after = assertIs<MarkupShape.Text>(before.withHandle(topLeft, target.x, target.y))

        assertTrue(after.width > before.width, "the drag did not grow the caption")
        assertTrue(after.at.x < before.at.x && after.at.y < before.at.y, "`at` did not move")
        val moved = textBounds(after)
        assertTrue(abs(moved.right - box.right) < 1.5f, "${moved.right} left ${box.right}")
        assertTrue(abs(moved.bottom - box.bottom) < 1.5f, "${moved.bottom} left ${box.bottom}")
    }

    /**
     * The clamp read back, which is the reason `scaled` computes the ratio from its own
     * result: at the top of the size range the glyphs stop growing, and the box must stop
     * with them rather than run on.
     */
    @Test
    fun `a drag past the size ceiling stops the box at the same factor as the type`() {
        val before = caption(size = 256f, wrap = 400f)
        val box = textBounds(before)
        // Four times the diagonal — 1024 px of type, twice the 512 px ceiling.
        val target = box.topLeft + (box.bottomRight - box.topLeft) * 4f

        val after = assertIs<MarkupShape.Text>(before.withHandle(bottomRight, target.x, target.y))

        assertEquals(512f, after.width)
        assertEquals(800f, after.wrap, 0.01f, "the box grew past the type it wraps")
    }

    /**
     * The proportion claim as something that can fail: the wrap width scales with the
     * size, so the same string breaks into the same number of lines.
     */
    @Test
    fun `a wrapped caption keeps its line count through a corner drag`() {
        val before = caption(size = 48f, wrap = 220f)
        val box = textBounds(before)
        val target = box.topLeft + (box.bottomRight - box.topLeft) * 2.5f
        val lines = requireNotNull(textLayoutOf(before)).lineCount

        val after = assertIs<MarkupShape.Text>(before.withHandle(bottomRight, target.x, target.y))

        assertTrue(lines > 1, "the fixture did not wrap, so the assertion proves nothing")
        assertEquals(lines, requireNotNull(textLayoutOf(after)).lineCount)
    }

    /**
     * **The `Text size` row is the same operation** (ME-148). It wrote `width` directly
     * before, leaving `wrap` behind — so the slider reflowed a caption that a corner drag
     * kept intact, and the two controls disagreed about what a caption's proportions are.
     */
    @Test
    fun `the text size row scales the box with the type and leaves the origin alone`() {
        val before = caption()
        val set = MarkupTool.defaults()
            .with(MarkupTool.Shapes, listOf(before))
            .with(MarkupTool.TextSize, 96f)

        val after = assertIs<MarkupShape.Text>(
            requireNotNull(MarkupTool.restyled(MarkupTool.TextSize, set, "t"))[MarkupTool.Shapes]
                .single()
        )

        assertEquals(96f, after.width)
        assertEquals(600f, after.wrap, 0.01f, "the box did not follow the type")
        assertEquals(before.at, after.at, "the slider moved the caption")
    }

    /** Dragging a corner through its opposite must not invert the caption. */
    @Test
    fun `a drag past the anchor leaves a caption that still has a size`() {
        val before = caption()
        val box = textBounds(before)
        val beyond = box.topLeft + (box.topLeft - box.bottomRight)

        val after = assertIs<MarkupShape.Text>(before.withHandle(bottomRight, beyond.x, beyond.y))

        assertTrue(after.width > 0f, "the caption inverted to ${after.width}")
        assertTrue(after.wrap > 0f)
    }
}
