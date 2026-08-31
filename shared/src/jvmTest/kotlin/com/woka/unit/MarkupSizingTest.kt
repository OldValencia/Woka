package com.woka.unit

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.tools.markup.MARKUP_SIZE_RANGE
import com.woka.feature.editor.tools.markup.MarkupKind
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.markupLines
import com.woka.feature.editor.tools.markup.markupRows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val SMALL = IntSize(300, 400)
private val LARGE = IntSize(8_000, 6_000)
private val HUGE = IntSize(30_000, 40_000)

/** The size rows are keyed on the pen, so each is asked for with its own pen chosen. */
private fun textDefault(size: IntSize): Float =
    markupRows(ParamSet().with(MarkupTool.Kind, MarkupKind.Text), size)
        .first { it.key == MarkupTool.TextSize.key }.default as Float

private fun strokeDefault(size: IntSize): Float =
    markupRows(ParamSet().with(MarkupTool.Kind, MarkupKind.Arrow), size)
        .first { it.key == MarkupTool.Width.key }.default as Float

/**
 * ME-159 — the default a markup shape starts at follows the picture that was opened.
 *
 * **The defect was about the first action, not about legibility.** Both sizes were
 * constants in source pixels, so placing a caption was two actions on every file but the
 * one size the constant suited: place it, then go and fix it. These assert the number
 * moves with the source, not that any particular size looks right — that is a matter for
 * the eye.
 */
class MarkupSizingTest {

    @Test
    fun `the text default scales with the shorter edge`() {
        // 6 % of 300 against 6 % of 6000: the ratio of the shorter edges, which is what
        // makes a caption the same fraction of the frame on both.
        assertEquals(18f, textDefault(SMALL), 0.01f)
        assertEquals(360f, textDefault(LARGE), 0.01f)
    }

    @Test
    fun `the stroke default scales with it too`() {
        assertEquals(2f, strokeDefault(SMALL), 0.01f)
        assertEquals(36f, strokeDefault(LARGE), 0.01f)
    }

    /**
     * The consequence of a **constant** range, written down rather than hidden: past a
     * shorter edge of about 16 700 px the derived default would leave the track, so it is
     * clamped to the ceiling. Still fifteen times the 64 px it used to be.
     */
    @Test
    fun `a default that would leave the range is clamped into it`() {
        assertEquals(MARKUP_SIZE_RANGE.endInclusive, textDefault(HUGE), 0.01f)
        assertTrue(strokeDefault(HUGE) in MARKUP_SIZE_RANGE, "${strokeDefault(HUGE)}")
    }

    /** A source of no known size falls back to what every existing document was written against. */
    @Test
    fun `an unknown source keeps the old constants`() {
        assertEquals(64f, textDefault(IntSize.Zero), 0.01f)
        assertEquals(6f, strokeDefault(IntSize.Zero), 0.01f)
    }

    /** Every derived default has to be reachable on the track that writes it. */
    @Test
    fun `every derived default lands inside the offered range`() {
        listOf(SMALL, LARGE, HUGE, IntSize(1, 1), IntSize(1_000, 1_000)).forEach { size ->
            assertTrue(textDefault(size) in MARKUP_SIZE_RANGE, "text at $size")
            assertTrue(strokeDefault(size) in MARKUP_SIZE_RANGE, "stroke at $size")
        }
    }

    /**
     * The trap this ticket walked into once: a re-defaulted spec is a **copy**, so it is
     * not `==` the declared one. `markupLines` compared by identity and silently dropped
     * both size controls off the compact bar.
     */
    @Test
    fun `the compact bar still carries the size row after it is re-defaulted`() {
        val rows = markupRows(ParamSet().with(MarkupTool.Kind, MarkupKind.Text), LARGE)
        val style = markupLines(rows).last()

        assertTrue(style.any { it.key == MarkupTool.TextSize.key }, "no size control on the bar")
        assertEquals(360f, style.first { it.key == MarkupTool.TextSize.key }.default)
    }

    /** The key and the range are what a written value is read back through — neither may move. */
    @Test
    fun `a derived spec keeps the key and the range of the declared one`() {
        val derived = markupRows(ParamSet().with(MarkupTool.Kind, MarkupKind.Text), LARGE)
            .first { it.key == MarkupTool.TextSize.key }

        assertEquals(MarkupTool.TextSize.key, derived.key)
        assertEquals(MARKUP_SIZE_RANGE, (derived as FloatParam).range)
    }
}
