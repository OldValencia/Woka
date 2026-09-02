package com.woka.integration

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.engine.image.RasterRecipe
import com.woka.feature.editor.engine.image.RasterStage
import com.woka.feature.editor.save.renderAtNativeScale
import com.woka.support.fixtureShader
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.IRect
import org.jetbrains.skia.ImageInfo
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The fourth stage on the pixels (ME-165).
 *
 * Driven through `renderAtNativeScale` rather than a window: it is the save path, it
 * needs no composition, and since this ticket both draw routines are literally the same
 * function — so what is worth asserting is what that function does, not that two callers
 * of it agree.
 *
 * Every assertion here is one the chain can plausibly get wrong: the local matrix
 * inverted, the colour filter applied on the wrong side, two shaders swapped, or the
 * scale never reaching the shader at all.
 */
class RasterShaderTest {

    private val size = IntSize(64, 32)

    /** Left half red, right half blue — asymmetric, so a coordinate error shows. */
    private fun halves(): ImageBitmap {
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(size.width, size.height, ColorAlphaType.PREMUL))
        bitmap.erase(0xFFFF0000.toInt())
        bitmap.erase(0xFF0000FF.toInt(), IRect.makeLTRB(32, 0, 64, 32))
        bitmap.setImmutable()
        return bitmap.asComposeImageBitmap()
    }

    /** Flat mid-grey, for the arithmetic assertions where position is noise. */
    private fun grey(value: Int): ImageBitmap {
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(size.width, size.height, ColorAlphaType.PREMUL))
        bitmap.erase(0xFF000000.toInt() or (value shl 16) or (value shl 8) or value)
        bitmap.setImmutable()
        return bitmap.asComposeImageBitmap()
    }

    private fun recipe(
        shaders: List<RasterStage.Shader> = emptyList(),
        colorFilter: ColorFilter? = null
    ) = RasterRecipe(
        matrix = Matrix(),
        outputSize = size,
        colorFilter = colorFilter,
        shaders = shaders,
        paints = emptyList(),
        skipped = emptyList()
    )

    private fun render(source: ImageBitmap, recipe: RasterRecipe) =
        renderAtNativeScale(source, size, recipe).toComposeImageBitmap().toPixelMap()

    /**
     * The local matrix, and the only test that catches it inverted. Sampling in source
     * coordinates means the halves come out where they went in; the wrong scale draws a
     * corner of the picture stretched across the whole frame, so the right half turns red.
     */
    @Test
    fun `the chain samples the picture in source coordinates`() {
        val pixels = render(halves(), recipe(listOf(fixtureShader())))
        assertEquals(0xFFFF0000.toInt(), pixels[8, 16].toArgb(), "the left half is not red")
        assertEquals(0xFF0000FF.toInt(), pixels[56, 16].toArgb(), "the right half is not blue")
    }

    /**
     * **The colour filter runs first, and the operations do not commute.** An offset
     * after a gain is not a gain after an offset: 0.5 doubled then lifted by 0.25 is
     * 1.0, while 0.5 lifted then doubled clips at 1.0 too — so the gain is 0.5 here,
     * where the two orders give 0.5 and 0.375 and only one of them can be right.
     */
    @Test
    fun `the colour filter runs before the shader`() {
        val lift = ColorMatrix().apply { values[4] = 0.5f * 255f }
        val pixels = render(
            grey(0),
            recipe(listOf(fixtureShader(gain = 0.5f)), ColorFilter.colorMatrix(lift))
        )
        val red = pixels[16, 16].red
        assertTrue(abs(red - 0.25f) < 0.02f, "filter-then-shader would give 0.25, got $red")
    }

    /** Two shaders are a sequence, and the fold's order is the one that runs. */
    @Test
    fun `two shaders run in the order the recipe lists them`() {
        val halved = render(grey(128), recipe(listOf(fixtureShader(gain = 0.5f), fixtureShader(offset = 0.25f))))
        val lifted = render(grey(128), recipe(listOf(fixtureShader(offset = 0.25f), fixtureShader(gain = 0.5f))))
        assertTrue(
            abs(halved[16, 16].red - lifted[16, 16].red) > 0.05f,
            "the two orders produced the same pixel, so the list order is not being honoured"
        )
    }

    /**
     * `resolutionScale` reaches the shader and means source pixels. A tap of 32 from
     * x = 8 lands at 40, which is past the boundary and therefore blue; at half scale
     * the same tap moves 16 and lands at 24, still red.
     */
    @Test
    fun `the tap distance is measured in source pixels`() {
        val full = render(halves(), recipe(listOf(fixtureShader(tapX = 32f))))
        assertEquals(0xFF0000FF.toInt(), full[8, 16].toArgb(), "a 32 px tap did not cross the boundary")

        val scaled = recipe(listOf(fixtureShader(tapX = 32f).copy(resolutionScale = 0.5f)))
        assertEquals(0xFFFF0000.toInt(), render(halves(), scaled)[8, 16].toArgb(), "the scale did not shorten the tap")
    }

    /** The path everything before ME-165 took, unchanged: no shader, no chain. */
    @Test
    fun `a recipe with no shader draws the picture as it always did`() {
        val pixels = render(halves(), recipe())
        assertEquals(0xFFFF0000.toInt(), pixels[8, 16].toArgb())
        assertEquals(0xFF0000FF.toInt(), pixels[56, 16].toArgb())
    }
}
