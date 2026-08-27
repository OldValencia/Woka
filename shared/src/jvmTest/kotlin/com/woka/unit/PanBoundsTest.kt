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
    fun `an axis that does not overflow can still be moved half a panel`() {
        // The rule that replaced "pinned to centre" on 2026-08-28: a picture that
        // fits is movable, as far as its own centre reaching the panel's edge.
        assertEquals(500f to 500f, panLimits(FIT, PANEL, LANDSCAPE))
    }

    @Test
    fun `at fill the two rules happen to agree`() {
        // X overflows by exactly a panel width, so half of it is half a panel — the
        // same number the floor gives Y. The case that tells them apart is below.
        assertEquals(500f to 500f, panLimits(FILL, PANEL, LANDSCAPE))
    }

    @Test
    fun `the larger of the two rules wins on each axis`() {
        val (limitX, limitY) = panLimits(SizeMode.Zoom(800), PANEL, LANDSCAPE)
        // X: 4000 shown over a 1000 panel, so the overflow rule, and unchanged by
        // the floor. Y: 2000 over 1000 gives 500, which is also the floor.
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
        // 50 on Y is well inside the floor and survives untouched; 900 on X is past
        // the overflow limit and is cut back to it.
        assertEquals(500f to 50f, clampOffset(900f, 50f, FILL, PANEL, LANDSCAPE))
        val (x, y) = clampOffset(-900f, -50f, FILL, PANEL, LANDSCAPE)
        assertEquals(-500f, x)
        assertEquals(-50f, y)
    }

    @Test
    fun `clamping leaves an offset inside the limits alone`() {
        assertEquals(120f to 0f, clampOffset(120f, 0f, FILL, PANEL, LANDSCAPE))
    }

    @Test
    fun `a fitting picture keeps the offset it was dragged to`() {
        // This asserted the opposite until 2026-08-28 — the clamp used to pull a
        // fitting picture back to centre on every drag end and every zoom step.
        assertEquals(400f to 400f, clampOffset(400f, 400f, FIT, PANEL, LANDSCAPE))
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
    fun `a fitting axis resists only past its own floor`() {
        // Inside the floor there is nothing to resist: the drag is followed exactly.
        assertEquals(200f, rubberBandOffset(0f, 200f, FIT, PANEL, LANDSCAPE).second)

        val (_, y) = rubberBandOffset(0f, 600f, FIT, PANEL, LANDSCAPE)
        assertEquals(535f, y)
        assertTrue(abs(y) < 600f, "the edge must resist")
    }
}
