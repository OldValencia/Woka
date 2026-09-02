package com.woka.unit

import androidx.compose.ui.graphics.ImageBitmap
import com.woka.core.data.TransferKind
import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.feature.viewer.video.ASSUMED_WHITE_POINT
import com.woka.feature.viewer.video.measuredWhitePoint
import com.woka.fixtures.HDR_REAL_PQ_AVIF
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_JPG
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The tone curve's white point comes from the picture, not from a literal (ME-120).
 *
 * The claim under test is a *distance*, not a value. `HDR_SHADER` divided by a hard 11.2
 * — 1120 nits asserted of every HDR source — and the corpus's one genuine PQ still peaks
 * around 226 nits. So what has to be proved is that the measurement lands in the picture's
 * own neighbourhood and nowhere near the constant; the exact figure belongs to the
 * fixture's encode and would make this test a transcript of it.
 */
class HdrWhitePointTest {

    /** The fixture's own peak, in shader units — nothing may measure above it. */
    private val fixturePeak = 2.94f

    private fun decode(name: String): ImageBitmap {
        val file = SampleMedia.file(name)
        return stillDecoderFor(file).frame(file, 0)
    }

    @Test
    fun `the real PQ still measures its own range, not the assumed one`() {
        val measured = measuredWhitePoint(decode(HDR_REAL_PQ_AVIF), TransferKind.PQ)

        // Measured 2026-09-03 over all 57600 pixels: peak code 0.620 = 2.94 units, 99.9th
        // percentile 0.573 = 1.88. The window is the distance between the 99th (0.66) and
        // the peak, so a percentile or a histogram that quietly moved would fail here.
        assertTrue(measured in 1.5f..2.94f, "measured $measured, expected the picture's own range")
        assertTrue(
            measured < ASSUMED_WHITE_POINT / 3f,
            "measured $measured is not meaningfully below the assumed $ASSUMED_WHITE_POINT"
        )
        assertTrue(measured <= fixturePeak, "measured $measured is above the picture's own peak")
    }

    @Test
    fun `an SDR picture is left alone rather than tone mapped`() {
        assertEquals(1f, measuredWhitePoint(decode(STILL_JPG), TransferKind.SDR))
    }

    @Test
    fun `the white point never drops below reference white`() {
        // The clamp, stated directly: `hable(x) / hable(W)` with W under 1.0 *expands*
        // the picture, which is the same defect as today pointing the other way. A black
        // frame is the case that would produce it.
        val black = ImageBitmap(8, 8)
        assertTrue(measuredWhitePoint(black, TransferKind.PQ) >= 1f)
    }
}
