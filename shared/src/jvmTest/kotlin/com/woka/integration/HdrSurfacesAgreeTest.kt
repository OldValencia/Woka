package com.woka.integration

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.woka.core.data.TransferKind
import com.woka.feature.editor.engine.image.EditorProxy
import com.woka.feature.viewer.image.ImageLoader
import com.woka.feature.viewer.image.readStillColor
import com.woka.fixtures.HDR_REAL_PQ_AVIF
import com.woka.fixtures.SampleMedia
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The viewer and the editor draw the same HDR picture (ME-177).
 *
 * **This is the assertion the ticket exists for, and it was false.** The viewer tone
 * mapped in its painter; the editor has its own draw path and never did, so the same
 * photograph was bright in one surface and dull in the other. Nothing caught it because
 * no test had ever put the two side by side — each was correct against its own idea of
 * what a decode returns.
 *
 * Compared at the proxy's scale, since that is what the editor's canvas holds. The two
 * decode at different sizes, so the claim is about **tone**, not about a bit-for-bit
 * match: the same picture through both paths must have the same brightness.
 */
class HdrSurfacesAgreeTest {

    /**
     * Within a couple of levels out of 255. The paths resample differently — the editor
     * assembles a tiled source at the proxy cap, the viewer decodes whole — so demanding
     * equality would be testing the scaler, not the colour.
     */
    private val tolerance = 2f

    @Test
    fun `the real PQ still is the same brightness in both surfaces`() {
        val file = SampleMedia.file(HDR_REAL_PQ_AVIF)

        assertEquals(TransferKind.PQ, readStillColor(file).transfer, "the fixture stopped being PQ")

        val viewer = meanLuma(ImageLoader.load(file, 0))
        val editor = meanLuma(EditorProxy.load(file, 0))

        assertTrue(
            abs(viewer - editor) < tolerance,
            "viewer drew $viewer and the editor drew $editor — the defect ME-177 fixed"
        )
    }

    @Test
    fun `and it is not merely that both are unmapped`() {
        // The test above passes just as well if *neither* surface maps, which is the
        // state this ticket found — so the pair is pinned to the mapped side.
        //
        // Pinned on the peak rather than the mean, and that took measuring: mapping this
        // fixture moves the mean from 41.9 to 38.0, slightly *down*, because PQ is
        // absolute and half the picture sits at 2.3 nits. It moves the peak from 156.6 to
        // 255.0. Only the second separates a mapped picture from raw codes.
        val file = SampleMedia.file(HDR_REAL_PQ_AVIF)
        assertTrue(
            peakLuma(EditorProxy.load(file, 0)) > 250f,
            "the editor is drawing unmapped PQ codes again"
        )
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
        return peak * 255f
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
        return (total / (bitmap.width * bitmap.height) * 255f).toFloat()
    }
}
