package com.woka.integration

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.engine.image.RasterRecipe
import com.woka.feature.editor.engine.image.RasterStage
import com.woka.feature.editor.save.renderAtNativeScale
import com.woka.feature.editor.tools.adjust.noiseShaderStage
import com.woka.feature.editor.tools.adjust.sharpenShaderStage
import com.woka.feature.editor.tools.adjust.toneShaderStage
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.IRect
import org.jetbrains.skia.ImageInfo
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ME-162's four adjustments actually drawn.
 *
 * **The unit tests cannot reach what this does.** Constructing a stage compiles its SkSL
 * — the effects are `by lazy` — so a syntax error is already caught there. What is not
 * is a *uniform count* that disagrees with the layout Skia computed: that throws when
 * something draws, and nothing drew until this file.
 *
 * The assertions are invariants rather than numbers, because the numbers are a look and
 * the author settles those on screen. A convolution over a flat picture must return it
 * unchanged; a tone slider must move a pixel in the direction it names.
 */
class AdjustShaderRenderTest {

    private val size = IntSize(32, 32)

    private fun flat(value: Int): ImageBitmap = bitmap { it.erase(argb(value)) }

    /** An edge, which is the only thing a sharpener has to work with. */
    private fun edge(): ImageBitmap = bitmap {
        it.erase(argb(60))
        it.erase(argb(200), IRect.makeLTRB(16, 0, 32, 32))
    }

    private fun bitmap(fill: (Bitmap) -> Unit): ImageBitmap {
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(size.width, size.height, ColorAlphaType.PREMUL))
        fill(bitmap)
        bitmap.setImmutable()
        return bitmap.asComposeImageBitmap()
    }

    private fun argb(value: Int) = (0xFF shl 24) or (value shl 16) or (value shl 8) or value

    private fun red(source: ImageBitmap, stage: RasterStage.Shader, x: Int): Float =
        renderAtNativeScale(
            source,
            size,
            RasterRecipe(
                matrix = Matrix(),
                outputSize = size,
                colorFilter = null,
                shaders = listOf(stage),
                paints = emptyList(),
                skipped = emptyList()
            )
        ).toComposeImageBitmap().toPixelMap()[x, 16].red

    @Test
    fun `shadows lift a dark pixel and highlights pull a bright one down`() {
        val lifted = red(flat(40), assertNotNull(toneShaderStage(0f, 100f)), 16)
        assertTrue(lifted > 40 / 255f + 0.05f, "Shadows did not lift a dark pixel: $lifted")

        val pulled = red(flat(230), assertNotNull(toneShaderStage(-100f, 0f)), 16)
        assertTrue(pulled < 230 / 255f - 0.05f, "Highlights did not pull a bright pixel down: $pulled")
    }

    /**
     * **The invariant that catches a broken kernel.** A 3×3 tent over a flat picture is
     * that picture, so the unsharp mask adds exactly zero — any drift means the weights
     * do not sum to their divisor, and on a photograph that reads as a shifted exposure
     * nobody asked for.
     */
    @Test
    fun `sharpening a flat picture changes nothing`() {
        val sharpened = red(flat(128), assertNotNull(sharpenShaderStage(100f)), 16)
        assertTrue(abs(sharpened - 128 / 255f) < 0.01f, "a flat picture moved: $sharpened")
    }

    /** And on an edge it does something: the dark side of the boundary is pushed darker. */
    @Test
    fun `sharpening an edge overshoots it`() {
        val plain = red(edge(), assertNotNull(sharpenShaderStage(0.001f)), 15)
        val sharpened = red(edge(), assertNotNull(sharpenShaderStage(100f)), 15)
        assertTrue(sharpened < plain - 0.02f, "the edge was not sharpened: $plain to $sharpened")
    }

    /**
     * Positive **adds** grain, which is the direction the name demands — the first cut
     * had it the other way round and it was caught by hand.
     */
    @Test
    fun `noise adds grain going up and removes it going down`() {
        // Across several pixels, not one: grain is a hash, and any single pixel is free
        // to land near the middle of the range and move by nothing at all.
        val grain = assertNotNull(noiseShaderStage(100f))
        val moved = (4..28).count { x -> abs(red(flat(128), grain, x) - 128 / 255f) > 0.01f }
        assertTrue(moved > 12, "positive noise moved only $moved of 25 pixels")

        // Denoise over a flat picture has nothing to average away, so it must not move.
        val denoised = red(flat(128), assertNotNull(noiseShaderStage(-100f)), 16)
        assertTrue(abs(denoised - 128 / 255f) < 0.01f, "negative noise added something: $denoised")
    }

    /** Grain is hashed from the coordinate, so the same pixel draws the same every time. */
    @Test
    fun `grain does not change between draws`() {
        val first = red(flat(128), assertNotNull(noiseShaderStage(100f)), 20)
        val second = red(flat(128), assertNotNull(noiseShaderStage(100f)), 20)
        assertEquals(first, second, "grain was re-rolled between draws")
    }
}
