package com.woka.unit

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.woka.feature.viewer.image.displayable
import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.fixtures.HDR_REAL_PQ_AVIF
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_JPG
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The seam every still passes through on its way to being drawn (ME-177).
 *
 * Two claims, and the second matters as much as the first: an HDR picture must come back
 * **changed**, and an SDR one must come back as the very same object. Almost every
 * picture in a library is SDR, and a full-size surface allocated to discover that would
 * be a cost paid by everything to serve almost nothing — the contract `StillEdits.apply`
 * already sets for the identical reason.
 */
class StillForDisplayTest {

    @Test
    fun `an SDR picture comes back as the same instance`() {
        val file = SampleMedia.file(STILL_JPG)
        val decoded = stillDecoderFor(file).frame(file, 0)
        assertSame(decoded, displayable(file, decoded), "an SDR still paid for a surface")
    }

    @Test
    fun `a PQ picture comes back tone mapped`() {
        val file = SampleMedia.file(HDR_REAL_PQ_AVIF)
        val decoded = stillDecoderFor(file).frame(file, 0)
        val drawn = displayable(file, decoded)

        assertNotSame(decoded, drawn, "the HDR still was returned untouched")
        assertTrue(drawn.width == decoded.width && drawn.height == decoded.height)
    }

    @Test
    fun `the mapped picture reaches full scale where the codes did not`() {
        // **The claim is about the peak, not the mean.** Measured on this fixture
        // 2026-09-03: raw mean 41.9 / peak 156.6, mapped mean 38.0 / peak 255.0. The mean
        // barely moves and slightly *falls*, because PQ is an absolute curve and this
        // picture is genuinely dark — half its pixels sit at 2.3 nits. Reading those codes
        // as sRGB lifts the shadows, which is why "tone mapped is brighter overall" is
        // false and was worth measuring rather than assuming.
        //
        // What the map actually buys is the top: the picture's own brightest tone becomes
        // white instead of stopping at 157/255, which is the range ME-120's white point
        // exists to hand back.
        val file = SampleMedia.file(HDR_REAL_PQ_AVIF)
        val raw = stillDecoderFor(file).frame(file, 0)
        val mapped = displayable(file, raw)

        assertTrue(peakLuma(raw) * 255f < 170f, "the raw codes already reached full scale")
        assertTrue(peakLuma(mapped) * 255f > 250f, "the mapped picture never reaches white")
    }

    private fun peakLuma(bitmap: ImageBitmap): Float {
        val pixels = bitmap.toPixelMap()
        var peak = 0f
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val c = pixels[x, y]
                val l = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
                if (l > peak) peak = l
            }
        }
        return peak
    }

    private fun meanLuma(bitmap: ImageBitmap): Float {
        val pixels = bitmap.toPixelMap()
        var total = 0.0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val c = pixels[x, y]
                total += 0.2126 * c.red + 0.7152 * c.green + 0.0722 * c.blue
            }
        }
        return (total / (bitmap.width * bitmap.height)).toFloat()
    }
}
