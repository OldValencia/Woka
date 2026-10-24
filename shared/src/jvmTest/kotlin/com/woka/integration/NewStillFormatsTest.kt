package com.woka.integration

import androidx.compose.ui.graphics.ImageBitmap
import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.fixtures.SampleMedia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val FIXTURE_WIDTH = 64
private const val FIXTURE_HEIGHT = 48

/** Every fixture is ImageMagick's `gradient:red-blue`: red along the top row, blue along the bottom. */
private val GRADIENT_FIXTURES = listOf("still.exr", "still.dds", "still.xpm", "still.pnm")

/**
 * The formats photo-formats PF-001 routed to the bundled FFmpeg, decoded the way the
 * viewer decodes them.
 *
 * The colour checks are what catch a channel swizzle: the grabber hands over B,G,R,A
 * and the raster stores A,B,G,R, and a picture with red and blue swapped is still a
 * 64×48 picture.
 */
class NewStillFormatsTest {

    @Test
    fun `each new format decodes at its native size`() {
        (GRADIENT_FIXTURES + "still.xbm").forEach { name ->
            val bitmap = decode(name)
            assertEquals(FIXTURE_WIDTH, bitmap.width, name)
            assertEquals(FIXTURE_HEIGHT, bitmap.height, name)
        }
    }

    @Test
    fun `red stays red and blue stays blue`() {
        GRADIENT_FIXTURES.forEach { name ->
            val bitmap = decode(name)
            val top = pixel(bitmap, FIXTURE_WIDTH - 1, 0)
            val bottom = pixel(bitmap, FIXTURE_WIDTH - 1, FIXTURE_HEIGHT - 1)
            assertTrue(red(top) > blue(top), "$name top is ${top.toUInt().toString(16)}")
            assertTrue(blue(bottom) > red(bottom), "$name bottom is ${bottom.toUInt().toString(16)}")
        }
    }

    /**
     * Both fixtures fade alpha to zero at the left edge; BGR24 rendered that black. The
     * right edge is near-opaque rather than 255 — DXT5 quantises it to 251.
     */
    @Test
    fun `transparency survives the decode`() {
        listOf("still.dds", "still.xpm").forEach { name ->
            val bitmap = decode(name)
            assertTrue(alpha(pixel(bitmap, 0, 0)) < 255, "$name lost its alpha")
            assertTrue(alpha(pixel(bitmap, FIXTURE_WIDTH - 1, 0)) > 200, name)
        }
    }

    private fun decode(name: String): ImageBitmap {
        val file = SampleMedia.file(name)
        return stillDecoderFor(file).frame(file, 0)
    }

    private fun pixel(bitmap: ImageBitmap, x: Int, y: Int): Int {
        val buffer = IntArray(1)
        bitmap.readPixels(buffer, x, y, 1, 1)
        return buffer[0]
    }

    private fun alpha(argb: Int): Int = argb ushr 24

    private fun red(argb: Int): Int = (argb shr 16) and 0xFF

    private fun blue(argb: Int): Int = argb and 0xFF
}
