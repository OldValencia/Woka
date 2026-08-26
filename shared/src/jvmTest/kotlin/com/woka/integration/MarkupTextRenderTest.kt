package com.woka.integration

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.fold
import com.woka.feature.editor.save.renderAtNativeScale
import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.feature.editor.tools.markup.drawMarkup
import com.woka.feature.editor.tools.markup.textBounds
import com.woka.support.MainDispatcherRule
import com.woka.support.withFoldRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.jetbrains.skia.Surface
import org.junit.Rule
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Type on the picture, at the resolution the file is written in (ME-122).
 *
 * **The assertion worth having is that the size is a count of source pixels**, and what
 * makes it bite is drawing at a density that is not 1: a measurer reading the draw
 * scope's density sets the string twice as large on a HiDPI canvas as
 * `renderAtNativeScale` writes it, so a caption would land elsewhere in the saved file.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarkupTextRenderTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val source = IntSize(640, 320)

    private val caption =
        MarkupShape.Text("t", 0xFFFF9F0AL, 64f, MarkupPoint(60f, 80f), "Woka")

    /**
     * Drawn at **`Density(2f)`** — a HiDPI canvas — and measured against bounds expressed
     * in source pixels. They must agree: the draw routine applies no transform of its own,
     * so the only thing that could stretch the glyphs is a measurer reading the density.
     */
    @Test
    fun `type measures the same at any draw density`() {
        val ink = inkExtentOf(density = 2f)
        val bounds = textBounds(caption)

        assertTrue(ink.width > 0f, "nothing was drawn at all")
        // Inflated by a pixel: antialiasing puts partial coverage just outside the layout,
        // and a font's advance is not its ink extent.
        val allowed = bounds.inflate(2f)
        assertTrue(allowed.contains(ink.topLeft), "ink started at ${ink.topLeft}, outside $bounds")
        assertTrue(allowed.contains(ink.bottomRight), "ink ran to ${ink.bottomRight}, past $bounds")
    }

    /** And the same at 1.0, so the comparison above is between two agreeing numbers. */
    @Test
    fun `the ink extent at density one is the ink extent at density two`() {
        val one = inkExtentOf(density = 1f)
        val two = inkExtentOf(density = 2f)

        assertTrue(one.width > 0f, "nothing was drawn at all")
        // `abs`: the defect makes the density-2 extent *larger*, so a one-sided
        // comparison passes with it present. Verified by re-introducing it.
        assertTrue(abs(one.width - two.width) < 2f, "$one and $two disagree")
        assertTrue(abs(one.height - two.height) < 2f, "$one and $two disagree")
    }

    /**
     * The whole path a save takes: the tool's stage, the fold's paint list, and
     * `renderAtNativeScale` against a source-sized surface. Text has to arrive there as
     * every other shape does — outside the matrix, in source coordinates.
     */
    @Test
    fun `a text shape reaches the native render where its bounds say it is`() {
        withFoldRegistry(listOf(MarkupTool)) { registry, _ ->
            val op = ToolOp(
                MarkupTool.manifest.id,
                MarkupTool.defaults().with(MarkupTool.Shapes, listOf(caption))
            )
            val recipe = fold(EditDocument(ops = listOf(op)), registry, source)

            val rendered = renderAtNativeScale(
                source = ImageBitmap(source.width, source.height),
                sourceSize = source,
                recipe = recipe
            )
            val pixels = rendered.toComposeImageBitmap().toPixelMap()
            val bounds = textBounds(caption)

            // Ink inside the measured box, and none outside it.
            var inside = 0
            var outside = 0
            for (y in 0 until pixels.height) {
                for (x in 0 until pixels.width) {
                    if (pixels[x, y].alpha == 0f) continue
                    val at = Offset(x.toFloat(), y.toFloat())
                    if (bounds.inflate(2f).contains(at)) inside++ else outside++
                }
            }

            assertTrue(inside > 0, "the fold drew no type at native scale")
            assertTrue(outside == 0, "$outside pixels of type landed outside $bounds")
        }
    }

    /** The bounding box of every pixel the shape put down, drawn at [density]. */
    private fun inkExtentOf(density: Float): Rect =
        Surface.makeRasterN32Premul(source.width, source.height).use { surface ->
            CanvasDrawScope().draw(
                Density(density),
                LayoutDirection.Ltr,
                surface.canvas.asComposeCanvas(),
                Size(source.width.toFloat(), source.height.toFloat())
            ) {
                drawMarkup(caption)
            }

            val pixels = surface.makeImageSnapshot().toComposeImageBitmap().toPixelMap()
            var left = pixels.width
            var top = pixels.height
            var right = 0
            var bottom = 0
            for (y in 0 until pixels.height) {
                for (x in 0 until pixels.width) {
                    if (pixels[x, y].alpha == 0f) continue
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
            if (right < left) Rect.Zero
            else Rect(left.toFloat(), top.toFloat(), right.toFloat() + 1f, bottom.toFloat() + 1f)
        }
}
