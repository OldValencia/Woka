package com.woka.integration

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.TextFace
import com.woka.feature.editor.tools.markup.drawMarkup
import com.woka.feature.editor.tools.markup.textBounds
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The faces as **pixels** (ME-151) — the half `MarkupFacesTest` cannot reach.
 *
 * That one asserts the style carries a `TextDecoration`; a decoration that is carried and
 * never drawn would pass it. So this looks for the line itself, as a **row of ink spanning
 * the caption's width** — which two upright glyphs with a gap between them never produce on
 * their own, and which is exactly what a rule drawn through or under them does.
 *
 * **The row is found, not guessed.** The first shape of this test asserted on the vertical
 * centre of `textBounds`, and that is not where a strike lands: the box runs from the
 * ascent to the descent, so its middle sits above the baseline and above the rule. Asking
 * "which rows span?" asks the question the feature actually answers.
 */
class MarkupFacesRenderTest {

    private val source = IntSize(640, 320)

    private fun caption(vararg faces: TextFace) = MarkupShape.Text(
        id = "t",
        color = 0xFFFF9F0AL,
        width = 96f,
        at = MarkupPoint(60f, 80f),
        // Two upright strokes with a wide gap between them: no row of this is continuous
        // until something draws a line across it.
        text = "I I",
        faces = faces.toSet()
    )

    @Test
    fun `plain type never fills a row across its own width`() {
        assertTrue(spanningRows(caption()).isEmpty(), "plain type already spanned a row")
    }

    @Test
    fun `a strikethrough draws a rule through the glyphs`() {
        val glyphs = inkRows(caption())
        val rows = spanningRows(caption(TextFace.Strikethrough))

        assertTrue(rows.isNotEmpty(), "no rule was drawn at all")
        assertTrue(
            rows.all { it in glyphs.min()..glyphs.max() },
            "the strike landed at $rows, outside the glyphs at ${glyphs.min()}..${glyphs.max()}"
        )
    }

    /** And an underline draws the same rule **below** them, which is what makes it a different face. */
    @Test
    fun `an underline draws its rule under the glyphs`() {
        val glyphs = inkRows(caption())
        val rows = spanningRows(caption(TextFace.Underline))

        assertTrue(rows.isNotEmpty(), "no rule was drawn at all")
        assertTrue(
            rows.all { it > glyphs.max() },
            "the underline landed at $rows, not below the glyphs ending at ${glyphs.max()}"
        )
    }

    /** Rows carrying ink across at least four fifths of the caption's measured width. */
    private fun spanningRows(shape: MarkupShape.Text): List<Int> {
        val span = textBounds(shape).width * 0.8f
        return litPerRow(shape).withIndex().filter { it.value > span }.map { it.index }
    }

    /** Rows carrying any ink at all — where the glyphs themselves are. */
    private fun inkRows(shape: MarkupShape.Text): List<Int> =
        litPerRow(shape).withIndex().filter { it.value > 0 }.map { it.index }

    /** Lit pixels per row of the whole surface, with [shape] drawn at source scale. */
    private fun litPerRow(shape: MarkupShape.Text): IntArray =
        Surface.makeRasterN32Premul(source.width, source.height).use { surface ->
            CanvasDrawScope().draw(
                Density(1f),
                LayoutDirection.Ltr,
                surface.canvas.asComposeCanvas(),
                Size(source.width.toFloat(), source.height.toFloat())
            ) {
                drawMarkup(shape)
            }

            val pixels = surface.makeImageSnapshot().toComposeImageBitmap().toPixelMap()
            IntArray(pixels.height) { y ->
                (0 until pixels.width).count { x -> pixels[x, y].alpha > 0f }
            }
        }
}
