package com.woka.integration

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.drawMarkup
import com.woka.feature.editor.tools.markup.textBounds
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The plate behind a caption, on the pixels (ME-146).
 *
 * Its own file rather than more of `MarkupTextRenderTest`, which is at the size mandate.
 * The seam is the one the ticket draws: that file asserts type is measured in **source**
 * pixels whatever density it is drawn at, this one asserts that a second thing is drawn at
 * all, underneath, inside the same `RasterStage.Paint` — no new stage kind, so §3.2 stands.
 */
class MarkupPlateRenderTest {

    private val source = IntSize(640, 320)

    private val plate = 0xFF0A84FFL

    private fun caption(fill: Long, corner: Float = 25f) =
        MarkupShape.Text("t", 0xFFFF9F0AL, 64f, MarkupPoint(60f, 80f), "Woka", fill = fill, corner = corner)

    /**
     * **The corner is a per cent of the type's size** (asked for 2026-08-27), so the top-left
     * pixel of the plate's own box is picture at 25 % and plate at 0 % — that pixel is inside
     * the rectangle either way, and only the rounding decides whether anything was painted
     * there.
     */
    @Test
    fun `the corner row rounds the plate and zero squares it`() {
        val square = caption(plate, corner = 0f)
        val box = textBounds(square)
        val x = box.left.toInt() + 1
        val y = box.top.toInt() + 1

        assertEquals(Color(plate), render(square)[x, y], "0 % left the corner rounded")
        assertEquals(0f, render(caption(plate, corner = 100f))[x, y].alpha, "100 % drew a square corner")
    }

    /**
     * Two pixels, on the horizontal through the middle of the block: one two pixels inside
     * the left edge, which is padding rather than a glyph at a 12.8 px inset, and one five
     * pixels outside the right edge, which is picture.
     */
    @Test
    fun `an opaque fill paints the box and nothing beyond it`() {
        val shape = caption(plate)
        val bounds = textBounds(shape)
        val pixels = render(shape)

        assertEquals(
            Color(plate),
            pixels[(bounds.left + 2f).toInt(), bounds.center.y.toInt()],
            "the plate did not reach its own left edge"
        )
        assertEquals(
            0f,
            pixels[(bounds.right + 5f).toInt(), bounds.center.y.toInt()].alpha,
            "the plate ran past the bounds the hit test uses"
        )
    }

    /** `0L` — the `Fill` row's first swatch — draws no plate at all, only the glyphs. */
    @Test
    fun `a fill with no alpha paints nothing behind the type`() {
        val shape = caption(0L)
        val bounds = textBounds(shape)
        val pixels = render(shape)

        assertEquals(
            0f,
            pixels[(bounds.left + 2f).toInt(), bounds.center.y.toInt()].alpha,
            "a caption with no plate was given one"
        )
    }

    private fun render(shape: MarkupShape.Text) =
        Surface.makeRasterN32Premul(source.width, source.height).use { surface ->
            CanvasDrawScope().draw(
                Density(1f),
                LayoutDirection.Ltr,
                surface.canvas.asComposeCanvas(),
                Size(source.width.toFloat(), source.height.toFloat())
            ) {
                drawMarkup(shape)
            }
            surface.makeImageSnapshot().toComposeImageBitmap().toPixelMap()
        }
}
