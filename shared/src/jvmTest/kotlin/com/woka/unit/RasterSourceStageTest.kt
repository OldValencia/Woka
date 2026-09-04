package com.woka.unit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.woka.core.data.TransferKind
import com.woka.feature.editor.engine.image.RasterRecipe
import com.woka.feature.editor.engine.image.RasterStage
import com.woka.feature.editor.engine.image.drawRecipeImage
import com.woka.feature.editor.engine.image.scaledBy
import com.woka.feature.editor.engine.video.hdrStageFor
import com.woka.feature.viewer.video.engine.probe.HdrInfo
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Adds a fixed amount to red — an **offset**, which does not commute with the scale the
 * filter applies. That non-commuting pair is the only way to observe an order.
 */
private const val LIFT_RED = """
    uniform shader image;
    half4 main(float2 coord) {
        half4 c = image.eval(coord);
        return half4(c.r + 0.4, c.g, c.b, c.a);
    }
"""

private val SIZE = IntSize(8, 8)

/**
 * The slot a recipe converts its **signal** in, before anything grades it (ME-205).
 *
 * **The ordering is the whole test.** A tone map placed anywhere in `shaders` runs after
 * the colour filter, because the chain attaches the filter to the image shader first —
 * and a tone map after a grade means every adjustment was applied to a PQ-coded signal.
 * Proving that needs two operations that do not commute, which is what a channel scale
 * and a channel *offset* are: halve-then-add and add-then-halve land on different reds.
 */
class RasterSourceStageTest {

    private val liftRed by lazy { RuntimeEffect.makeForShader(LIFT_RED) }

    /**
     * Halves red. A **scale**, deliberately: `ColorMatrix`'s translation column is in
     * 0…255 while a shader works in 0…1, and a test that mixed the two conventions would
     * report an ordering failure that was only its own arithmetic — which is what the
     * first draft of this file did.
     */
    private fun halveRed() = ColorFilter.colorMatrix(ColorMatrix().apply { values[0] = 0.5f })

    private fun plain() = RasterRecipe(
        matrix = Matrix(),
        outputSize = SIZE,
        colorFilter = null,
        paints = emptyList(),
        skipped = emptyList()
    )

    /** One flat colour in, the red channel of the drawn result out. */
    private fun redAfter(recipe: RasterRecipe): Int {
        val source = ImageBitmap(SIZE.width, SIZE.height)
        val box = Size(SIZE.width.toFloat(), SIZE.height.toFloat())
        // 0.4 rather than full red, so neither order clamps and both results are real.
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(source), box) {
            drawRect(Color(0.4f, 0f, 0f))
        }
        val surface = Surface.makeRasterN32Premul(SIZE.width, SIZE.height)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            surface.canvas.asComposeCanvas(),
            box
        ) {
            drawRecipeImage(source, SIZE, recipe)
        }
        val read = Bitmap().apply { allocN32Pixels(SIZE.width, SIZE.height) }
        surface.readPixels(read, 0, 0)
        return (read.getColor(SIZE.width / 2, SIZE.height / 2) shr 16) and 0xFF
    }

    @Test
    fun `the source stage runs before the colour filter`() {
        val stage = RasterStage.Shader(liftRed)
        val filter = halveRed()

        // Source first: 0.4 lifted to 0.8, then halved to 0.4 — about 102.
        val asSource = redAfter(plain().copy(source = stage, colorFilter = filter))
        // As an effect it runs after the filter instead: 0.4 halved to 0.2, then lifted
        // to 0.6 — about 153. Which is the whole point: on an HDR frame that second
        // order grades a PQ signal and tone maps the result.
        val asEffect = redAfter(plain().copy(shaders = listOf(stage), colorFilter = filter))

        assertTrue(asSource < asEffect, "source=$asSource effect=$asEffect: the order did not hold")
    }

    @Test
    fun `a recipe with no source keeps the plain draw`() {
        // The fast path takes a `ColorFilter` and no shader at all, so this is the
        // assertion that the new field did not quietly move every still onto the chain.
        assertEquals(102, redAfter(plain()))
    }

    @Test
    fun `scaledBy carries the source through untouched`() {
        val stage = RasterStage.Shader(liftRed)
        val scaled = plain().copy(source = stage).scaledBy(0.5f)

        assertEquals(stage, scaled.source, "a reader's smaller bitmap lost the conversion")
        assertNull(scaled.source?.resolutionScale, "a tone map declared a resolution it never reads")
    }

    @Test
    fun `only a picture that needs managing gets a stage`() {
        assertNull(hdrStageFor(null), "a player that has not opened yet produced a stage")
        assertNull(
            hdrStageFor(HdrInfo(TransferKind.SDR, isWideGamut = false)),
            "an ordinary SDR frame produced a stage"
        )
        val pq = assertNotNull(hdrStageFor(HdrInfo(TransferKind.PQ, isWideGamut = true)))
        // `vec2 scale` first, and **(1, 1)**: the chain samples in source coordinates,
        // so the painter's `intrinsicSize / drawSize` ratio would blow one corner up.
        assertEquals(listOf(1f, 1f, 1f, 1f, 11.2f), pq.uniforms)
        assertNull(pq.resolutionScale)
    }
}
