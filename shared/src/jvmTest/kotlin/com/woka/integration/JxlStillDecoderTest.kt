package com.woka.integration

import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.fixtures.SampleMedia
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val WIDTH = 64
private const val HEIGHT = 48

/** Corners in reading order: (0,0), (63,0), (0,47), (63,47). */
private val CORNERS = listOf(0 to 0, WIDTH - 1 to 0, 0 to HEIGHT - 1, WIDTH - 1 to HEIGHT - 1)

/**
 * JPEG XL through the bundled libjxl. Expected values are `djxl`'s own decode of
 * each sample, read from the PNG bytes it wrote — `natives/jxl/README.md`.
 */
class JxlStillDecoderTest {

    @Test
    fun `lossless decodes exactly`() {
        assertCorners("jxl-lossless.jxl", 0, tolerance = 0, 0x0000FF, 0xFF0000, 0x00FFFF, 0xFFFF00)
    }

    /** Lossy and JPEG-recompressed may round differently from djxl's PNG path. */
    @Test
    fun `lossy and recompressed JPEG decode within rounding`() {
        assertCorners("jxl-lossy.jxl", 0, tolerance = 2, 0x0200FD, 0xFE000E, 0x16FEFF, 0xFCFF0C)
        assertCorners("jxl-jpeg-recompressed.jxl", 0, tolerance = 2, 0x000149, 0xFC014E, 0x02FE4D, 0xFFFE50)
    }

    @Test
    fun `alpha is kept`() {
        val alphas = cornerPixels("jxl-alpha.jxl", 0).map { it ushr 24 }
        assertEquals(listOf(255, 159, 159, 64), alphas)
    }

    @Test
    fun `an animation is a frame set with its durations`() {
        val file = SampleMedia.file("jxl-animated.jxl")
        val frames = stillDecoderFor(file).frameSet(file).frames
        assertEquals(3, frames.size)
        assertEquals(listOf(100, 100, 100), frames.map { it.durationMs })
        assertCorners("jxl-animated.jxl", 1, tolerance = 0, 0x00FF00, 0x00FF00, 0x00FF00, 0x00FF00)
        assertCorners("jxl-animated.jxl", 2, tolerance = 0, 0x0000FF, 0x0000FF, 0x0000FF, 0x0000FF)
    }

    @Test
    fun `the size is read without decoding`() {
        val file = SampleMedia.file("jxl-lossy.jxl")
        val size = stillDecoderFor(file).nativeSize(file, 0)
        assertEquals(WIDTH, size.width)
        assertEquals(HEIGHT, size.height)
    }

    private fun cornerPixels(name: String, frame: Int): List<Int> {
        val file = SampleMedia.file(name)
        val bitmap = stillDecoderFor(file).frame(file, frame)
        assertEquals(WIDTH, bitmap.width, name)
        return CORNERS.map { (x, y) ->
            IntArray(1).also { bitmap.readPixels(it, x, y, 1, 1) }[0]
        }
    }

    private fun assertCorners(name: String, frame: Int, tolerance: Int, vararg rgb: Int) {
        cornerPixels(name, frame).zip(rgb.toList()).forEachIndexed { corner, (actual, expected) ->
            for (shift in intArrayOf(0, 8, 16)) {
                val difference = abs((actual shr shift and 0xFF) - (expected shr shift and 0xFF))
                assertTrue(difference <= tolerance, "$name frame $frame corner $corner: ${actual.toUInt().toString(16)}")
            }
        }
    }
}
