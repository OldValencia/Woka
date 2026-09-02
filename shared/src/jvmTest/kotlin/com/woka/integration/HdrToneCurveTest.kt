package com.woka.integration

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.woka.core.data.TransferKind
import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.feature.viewer.video.ASSUMED_WHITE_POINT
import com.woka.feature.viewer.video.HdrImagePainter
import com.woka.feature.viewer.video.measuredWhitePoint
import com.woka.fixtures.HDR_REAL_PQ_AVIF
import com.woka.fixtures.SampleMedia
import org.jetbrains.skia.Surface
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What the white point is worth once the shader has actually run (ME-120).
 *
 * The defect was reported as *"brightness at −50 %"* on a real HDR photograph, and the
 * arithmetic behind that complaint is exact: `hable(1.0) / hable(11.2)` is 0.304, which
 * the OETF encodes as **148/255**. So the assertion is made at the same point the
 * complaint was — SDR reference white, the tone every picture has — rendered through the
 * real shader twice, once per divisor.
 *
 * **A one-pixel source, not the fixture.** The fixture supplies the *white point*, which
 * is a fact about a picture; the tone being checked has to be a known one, and no pixel of
 * a photograph is guaranteed to be reference white. PQ code 0.5081 is 100 nits by
 * definition of ST 2084.
 */
class HdrToneCurveTest {

    /** The PQ code for 100 nits — SDR reference white on an absolute curve. */
    private val referenceWhiteCode = 0.5081f

    private fun realFixtureWhitePoint(): Float {
        val file = SampleMedia.file(HDR_REAL_PQ_AVIF)
        return measuredWhitePoint(stillDecoderFor(file).frame(file, 0), TransferKind.PQ)
    }

    /** A flat patch at PQ [code]. Not 1×1: filtering and edges have nothing to say here. */
    private fun grey(code: Float): ImageBitmap {
        val level = (code * 255f).toInt().coerceIn(0, 255)
        return Surface.makeRasterN32Premul(PATCH, PATCH).use { surface ->
            surface.canvas.clear(0xFF000000.toInt() or (level shl 16) or (level shl 8) or level)
            surface.makeImageSnapshot().toComposeImageBitmap()
        }
    }

    /** [source] through the real shader with [whitePoint], read at the centre, as 0…255. */
    private fun drawThrough(source: ImageBitmap, whitePoint: Float): Int {
        val painter = HdrImagePainter(
            bitmap = source,
            transfer = TransferKind.PQ,
            isWideGamut = false,
            whitePoint = whitePoint
        )
        return Surface.makeRasterN32Premul(PATCH, PATCH).use { surface ->
            CanvasDrawScope().draw(
                Density(1f),
                LayoutDirection.Ltr,
                surface.canvas.asComposeCanvas(),
                Size(PATCH.toFloat(), PATCH.toFloat())
            ) {
                with(painter) { draw(size) }
            }
            val out = surface.makeImageSnapshot().toComposeImageBitmap().toPixelMap()
            (out[PATCH / 2, PATCH / 2].red * 255f).toInt()
        }
    }

    @Test
    fun `the assumed white point is what crushed reference white`() {
        val level = drawThrough(grey(referenceWhiteCode), ASSUMED_WHITE_POINT)
        assertTrue(level in 130..165, "reference white rendered at $level, expected the reported ~148")
    }

    @Test
    fun `measuring the picture restores reference white`() {
        val measured = realFixtureWhitePoint()
        val level = drawThrough(grey(referenceWhiteCode), measured)
        val assumed = drawThrough(grey(referenceWhiteCode), ASSUMED_WHITE_POINT)

        // 255 belongs to the picture's own brightest tone, not to reference white: a
        // fixture peaking above SDR must still leave room above it. What the fix owes is
        // the ~40 % of range the constant was taking away.
        assertTrue(level > 200, "reference white rendered at $level with white point $measured")
        assertTrue(level > assumed + 50, "measured $level barely beats assumed $assumed")
    }

    @Test
    fun `the picture's own peak is what reaches full scale`() {
        val measured = realFixtureWhitePoint()
        // `hable(x) / hable(W)` is 1.0 at x = W by construction, so the tone the white
        // point names is the one that comes out white. This is the property that makes
        // the measurement the right divisor rather than merely a smaller one.
        assertTrue(drawThrough(grey(pqCodeFor(measured)), measured) >= 250)
    }

    /** The inverse of the EOTF the measurement used, for turning units back into a code. */
    private fun pqCodeFor(units: Float): Float {
        val y = (units / 100f).pow(0.1593017578125f)
        return ((0.8359375f + 18.8515625f * y) / (1f + 18.6875f * y)).pow(78.84375f)
    }
}

/** Big enough that nothing in the draw path is an edge case; small enough to be free. */
private const val PATCH = 8
