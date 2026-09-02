package com.woka.unit

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.woka.core.data.TransferKind
import com.woka.feature.viewer.image.readStillColor
import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.fixtures.HDR_PQ_AVIF
import com.woka.fixtures.HDR_REAL_PQ_AVIF
import com.woka.fixtures.SampleMedia
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The corpus's one genuine HDR still is genuine (ME-172).
 *
 * **Read the tags and then distrust them.** Three files in this corpus carry a perfect PQ
 * description over ordinary SDR pixels, and the probe reports "PQ + wide" for every one —
 * which is exactly how they came to be relied on for a year and exactly why a tag
 * assertion alone would prove nothing here.
 *
 * The pixel claim is therefore made **against one of those fakes** rather than against a
 * number. Both files go through the same decoder, so whatever it does to PQ it does to
 * both, and what survives the comparison is the only thing that actually differs: one
 * holds a picture that peaks near the top of the range, the other holds one that does not.
 */
class HdrFixtureTest {

    private fun decode(name: String): ImageBitmap {
        val file = SampleMedia.file(name)
        return stillDecoderFor(file).frame(file, 0)
    }

    private fun peakLuma(bitmap: ImageBitmap): Float {
        val pixels = bitmap.toPixelMap()
        var peak = 0f
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val colour = pixels[x, y]
                val luma = 0.2126f * colour.red + 0.7152f * colour.green + 0.0722f * colour.blue
                if (luma > peak) peak = luma
            }
        }
        return peak
    }

    @Test
    fun `the real fixture describes itself as PQ over BT2020`() {
        val info = readStillColor(SampleMedia.file(HDR_REAL_PQ_AVIF))

        assertEquals(TransferKind.PQ, info.transfer)
        assertTrue(info.isWideGamut, "the primaries were not written as BT.2020")
        assertTrue(info.needsColorManagement)
    }

    /**
     * **The assertion the three tags-only files would fail.** They are an SDR `testsrc2`
     * pattern, so they reach the top of the range; a frame cut from a real master peaks
     * around 0.58 in PQ code, which is why `HdrShader`'s `W = 11.2` compresses it as
     * though it had eleven times the range it has (ME-120).
     */
    @Test
    fun `it holds HDR pixels rather than SDR ones wearing the tags`() {
        val real = peakLuma(decode(HDR_REAL_PQ_AVIF))
        val fake = peakLuma(decode(HDR_PQ_AVIF))

        assertTrue(real < fake - 0.1f, "the real fixture peaks like the fake one: $real vs $fake")
        assertTrue(real > 0.05f, "the fixture decoded to nothing at all: $real")
    }

    /** And it is a picture, not a placeholder: 320×180, the master's own aspect. */
    @Test
    fun `it decodes to the frame it was cut from`() {
        val bitmap = decode(HDR_REAL_PQ_AVIF)

        assertEquals(320, bitmap.width)
        assertEquals(180, bitmap.height)
    }

    /** The corpus reaches it by name through `SampleMedia`, never by absolute path. */
    @Test
    fun `it is reachable through the fixture object`() {
        assertTrue(SampleMedia.file(HDR_REAL_PQ_AVIF).length() > 0, "the fixture is missing")
    }
}
