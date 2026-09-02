package com.woka.unit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.engine.image.RasterRecipe
import com.woka.feature.editor.engine.image.fold
import com.woka.feature.editor.engine.image.scaledBy
import com.woka.feature.editor.core.ToolDomain
import com.woka.support.FOLD_SOURCE
import com.woka.support.StageTool
import com.woka.support.fixtureShader
import com.woka.support.toolOp
import com.woka.support.withFoldRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Moving a finished recipe into a smaller bitmap's coordinates (ME-109).
 *
 * **The case this exists for is a crop.** A recipe is folded in source pixels, and a
 * reader may have decoded the picture small — a 400 px grid cover of a 6000 px
 * photograph. Rotate and flip survive that mismatch because they are scale-free; a
 * crop rect of `(1200, 800, 600×400)` applied to the cover cuts a region twenty times
 * too large and lands off the picture entirely.
 */
class RasterRecipeTest {

    /** A crop: translate the origin to zero, and a frame of its own. */
    private fun crop(left: Float, top: Float, size: IntSize) = RasterRecipe(
        matrix = Matrix().apply { translate(-left, -top) },
        outputSize = size,
        colorFilter = null,
        paints = emptyList(),
        skipped = emptyList()
    )

    @Test
    fun `halving a crop halves its origin, its size and its output`() {
        val scaled = crop(1200f, 800f, IntSize(600, 400)).scaledBy(0.5f)

        assertEquals(IntSize(300, 200), scaled.outputSize)
        // The point the crop starts at, in the smaller bitmap, must land on the origin.
        assertEquals(Offset.Zero, scaled.matrix.map(Offset(600f, 400f)))
        // And a point one crop-width along stays one scaled crop-width along.
        assertEquals(Offset(300f, 0f), scaled.matrix.map(Offset(900f, 400f)))
    }

    /**
     * The identity path, and it is not merely an optimisation: a reader handed a
     * native bitmap has to come back bit-for-bit what it was before ME-109.
     */
    @Test
    fun `a factor of one returns the very same recipe`() {
        val recipe = crop(10f, 20f, IntSize(100, 50))

        assertSame(recipe, recipe.scaledBy(1f))
    }

    /** A nonsense factor is refused the same way, rather than producing a nonsense frame. */
    @Test
    fun `a zero or infinite factor changes nothing`() {
        val recipe = crop(10f, 20f, IntSize(100, 50))

        assertSame(recipe, recipe.scaledBy(0f))
        assertSame(recipe, recipe.scaledBy(-1f))
        assertSame(recipe, recipe.scaledBy(Float.NaN))
    }

    /**
     * Scaling into a thumbnail must not describe a zero-sized frame — the renderer
     * hands `outputSize` straight to a Skia surface.
     */
    @Test
    fun `a frame scaled below a pixel keeps one`() {
        val scaled = crop(0f, 0f, IntSize(8, 8)).scaledBy(0.01f)

        assertEquals(IntSize(1, 1), scaled.outputSize)
    }

    /** Scale-free work is unaffected by the trip through source coordinates. */
    @Test
    fun `a quarter turn scales to the same quarter turn`() {
        val turn = RasterRecipe(
            matrix = Matrix().apply { rotateZ(90f) },
            outputSize = IntSize(480, 640),
            colorFilter = null,
            paints = emptyList(),
            skipped = emptyList()
        )

        val scaled = turn.scaledBy(0.5f)

        assertEquals(IntSize(240, 320), scaled.outputSize)
        // (100, 0) turns to (0, 100) at either scale — the rotation itself is unchanged.
        val turned = scaled.matrix.map(Offset(100f, 0f))
        assertEquals(0f, turned.x, 0.001f)
        assertEquals(100f, turned.y, 0.001f)
    }

    /**
     * The fourth stage is a **sequence**, not a collapse (ME-165): two convolutions do
     * not compose the way two colour matrices do, so the fold keeps both and keeps the
     * order it folded them in.
     */
    @Test
    fun `the fold carries every shader stage, in domain order`() {
        val colour = StageTool("gain", ToolDomain.Color, fixtureShader(gain = 0.5f))
        val output = StageTool("offset", ToolDomain.Output, fixtureShader(offset = 0.25f))

        withFoldRegistry(listOf(output, colour)) { registry, _ ->
            // Clicked Output first; Colour must still run first, as §3.4 orders it.
            val document = EditDocument(ops = listOf(toolOp("offset"), toolOp("gain")))
            val shaders = fold(document, registry, FOLD_SOURCE).shaders

            assertEquals(2, shaders.size, "a shader stage was collapsed away")
            assertEquals(listOf(0.5f, 0f, 0f), shaders[0].uniforms, "Colour did not run first")
            assertEquals(listOf(1f, 0.25f, 0f), shaders[1].uniforms)
        }
    }

    /**
     * A radius stated in source pixels describes a different picture once the bitmap is
     * a fraction of the source, so the scale travels with the recipe — otherwise a grid
     * tile is sharpened as though the tile were the whole photograph.
     */
    @Test
    fun `scaling a recipe scales what its shaders call a source pixel`() {
        val recipe = RasterRecipe(
            matrix = Matrix(),
            outputSize = IntSize(600, 400),
            colorFilter = null,
            shaders = listOf(fixtureShader(tapX = 8f)),
            paints = emptyList(),
            skipped = emptyList()
        )

        val scaled = recipe.scaledBy(0.25f)

        assertEquals(0.25f, scaled.shaders.single().resolutionScale!!, 0.0001f)
        // The uniforms themselves are the tool's and are never rewritten.
        assertEquals(listOf(1f, 0f, 8f), scaled.shaders.single().uniforms)
    }
}
