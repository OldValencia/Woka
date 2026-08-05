package com.woka.unit

import androidx.compose.ui.unit.IntSize
import com.woka.core.data.ScalePreset
import com.woka.feature.viewer.shell.SizeMode
import com.woka.feature.viewer.shell.clampOffset
import com.woka.feature.viewer.shell.panLimits
import com.woka.feature.viewer.shell.rubberBandOffset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Panel 1000×1000 over 500×250: Fill shows 2000×1000, so only X can be panned. */
private val PANEL = IntSize(1000, 1000)
private val LANDSCAPE = IntSize(500, 250)
private val FILL = SizeMode.Preset(ScalePreset.Fill)
private val FIT = SizeMode.Preset(ScalePreset.Fit)

class PanBoundsTest {

    @Test
    fun `an axis that does not overflow is pinned to centre`() {
        assertEquals(0f to 0f, panLimits(FIT, PANEL, LANDSCAPE))
    }

    @Test
    fun `only the overflowing axis gets a limit`() {
        assertEquals(500f to 0f, panLimits(FILL, PANEL, LANDSCAPE))
    }

    @Test
    fun `both axes get a limit when the zoom passes fill`() {
        val (limitX, limitY) = panLimits(SizeMode.Zoom(800), PANEL, LANDSCAPE)
        assertEquals(1500f, limitX)
        assertEquals(500f, limitY)
    }

    @Test
    fun `degenerate sizes have no room to pan`() {
        assertEquals(0f to 0f, panLimits(FILL, IntSize.Zero, LANDSCAPE))
        assertEquals(0f to 0f, panLimits(FILL, PANEL, IntSize(500, 0)))
    }

    @Test
    fun `clamping never lets the frame past the edge`() {
        assertEquals(500f to 0f, clampOffset(900f, 50f, FILL, PANEL, LANDSCAPE))
        // Clamping a negative value into 0f..0f yields -0.0, which is the same
        // point on screen but not equal to 0f — compare the magnitude instead.
        val (x, y) = clampOffset(-900f, -50f, FILL, PANEL, LANDSCAPE)
        assertEquals(-500f, x)
        assertEquals(0f, abs(y))
    }

    @Test
    fun `clamping leaves an offset inside the limits alone`() {
        assertEquals(120f to 0f, clampOffset(120f, 0f, FILL, PANEL, LANDSCAPE))
    }

    @Test
    fun `a pinned axis is clamped back to centre`() {
        assertEquals(0f to 0f, clampOffset(400f, 400f, FIT, PANEL, LANDSCAPE))
    }

    @Test
    fun `the rubber band is inert inside the limits`() {
        assertEquals(300f to 0f, rubberBandOffset(300f, 0f, FILL, PANEL, LANDSCAPE))
        assertEquals(500f to 0f, rubberBandOffset(500f, 0f, FILL, PANEL, LANDSCAPE))
    }

    @Test
    fun `past the limit it resists instead of stopping dead`() {
        val (x, _) = rubberBandOffset(600f, 0f, FILL, PANEL, LANDSCAPE)
        assertEquals(535f, x)
        assertTrue(x > 500f, "the edge must still give a little")
        assertTrue(x < 600f, "the edge must resist")
    }

    @Test
    fun `resistance is symmetric about zero`() {
        val (positive, _) = rubberBandOffset(760f, 0f, FILL, PANEL, LANDSCAPE)
        val (negative, _) = rubberBandOffset(-760f, 0f, FILL, PANEL, LANDSCAPE)
        assertEquals(positive, -negative)
    }

    @Test
    fun `resistance is monotonic and decaying`() {
        var previousValue = 0f
        var previousGain = Float.MAX_VALUE
        (0..2000 step 100).forEach { raw ->
            val (banded, _) = rubberBandOffset(raw.toFloat(), 0f, FILL, PANEL, LANDSCAPE)
            assertTrue(banded >= previousValue, "the band went backwards at $raw")
            val gain = banded - previousValue
            if (raw > 500) {
                assertTrue(gain <= previousGain + 0.001f, "the band stopped decaying at $raw")
                assertTrue(gain < 100f, "the band gave back the full drag at $raw")
            }
            previousValue = banded
            previousGain = gain
        }
    }

    @Test
    fun `a pinned axis still rubber bands from centre`() {
        val (_, y) = rubberBandOffset(0f, 200f, FIT, PANEL, LANDSCAPE)
        assertEquals(70f, y)
        assertTrue(abs(y) < 200f)
    }
}
