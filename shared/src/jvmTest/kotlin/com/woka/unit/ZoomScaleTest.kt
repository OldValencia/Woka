package com.woka.unit

import androidx.compose.ui.unit.IntSize
import com.woka.core.data.ScalePreset
import com.woka.feature.viewer.shell.SizeMode
import com.woka.feature.viewer.shell.clampPercent
import com.woka.feature.viewer.shell.fillPercent
import com.woka.feature.viewer.shell.fitPercent
import com.woka.feature.viewer.shell.percentFromFraction
import com.woka.feature.viewer.shell.presetPercent
import com.woka.feature.viewer.shell.sizeModeFor
import com.woka.feature.viewer.shell.sliderFraction
import com.woka.feature.viewer.shell.snapToPreset
import com.woka.feature.viewer.shell.stepDown
import com.woka.feature.viewer.shell.stepUp
import com.woka.feature.viewer.shell.zoomBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Panel 1000×1000 over a 500×250 landscape: Fit is 200%, Fill is 400%. */
private val PANEL = IntSize(1000, 1000)
private val LANDSCAPE = IntSize(500, 250)

/** Panel narrower than the media on one axis: Fit 50%, Fill 100%. */
private val NARROW = IntSize(200, 400)
private val SQUARE = IntSize(400, 400)

class ZoomScaleTest {

    @Test
    fun `fit letterboxes on the tighter axis`() {
        assertEquals(200, fitPercent(PANEL, LANDSCAPE))
        assertEquals(50, fitPercent(NARROW, SQUARE))
    }

    @Test
    fun `fill covers on the looser axis`() {
        assertEquals(400, fillPercent(PANEL, LANDSCAPE))
        assertEquals(100, fillPercent(NARROW, SQUARE))
    }

    @Test
    fun `degenerate sizes fall back to native`() {
        assertEquals(100, fitPercent(IntSize.Zero, LANDSCAPE))
        assertEquals(100, fillPercent(IntSize.Zero, LANDSCAPE))
        assertEquals(100, fitPercent(PANEL, IntSize(0, 0)))
        assertEquals(100, fillPercent(PANEL, IntSize(500, 0)))
    }

    @Test
    fun `fit never rounds down to nothing`() {
        assertEquals(1, fitPercent(IntSize(10, 10), IntSize(16000, 16000)))
    }

    @Test
    fun `presets resolve on the one scale and Actual ignores the panel`() {
        assertEquals(200, presetPercent(ScalePreset.Fit, PANEL, LANDSCAPE))
        assertEquals(400, presetPercent(ScalePreset.Fill, PANEL, LANDSCAPE))
        assertEquals(100, presetPercent(ScalePreset.Original, PANEL, LANDSCAPE))
        assertEquals(100, presetPercent(ScalePreset.Original, NARROW, SQUARE))
    }

    @Test
    fun `bounds always contain every snap point`() {
        val cases = listOf(
            PANEL to LANDSCAPE,
            NARROW to SQUARE,
            IntSize.Zero to LANDSCAPE,
            PANEL to IntSize(1, 1),
            PANEL to IntSize(16000, 16000)
        )
        cases.forEach { (container, native) ->
            val bounds = zoomBounds(container, native)
            assertTrue(bounds.first >= 1, "lower bound must stay positive: $bounds")
            assertTrue(bounds.first <= bounds.last, "empty range for $container/$native")
            assertTrue(fitPercent(container, native) in bounds, "Fit unreachable in $bounds")
            assertTrue(fillPercent(container, native) in bounds, "Fill unreachable in $bounds")
            assertTrue(100 in bounds, "Actual unreachable in $bounds")
        }
    }

    @Test
    fun `bounds span an order of magnitude either side of fit`() {
        assertEquals(20..2000, zoomBounds(PANEL, LANDSCAPE))
    }

    @Test
    fun `clamp pins to the range`() {
        assertEquals(20, clampPercent(1, 20..2000))
        assertEquals(2000, clampPercent(99999, 20..2000))
        assertEquals(150, clampPercent(150, 20..2000))
    }

    @Test
    fun `steps are geometric and always move`() {
        val bounds = 10..1000
        assertEquals(110, stepUp(100, bounds))
        assertEquals(91, stepDown(100, bounds))
        assertEquals(12, stepUp(11, bounds))
        assertEquals(10, stepDown(11, bounds))
    }

    @Test
    fun `steps stop at the limits`() {
        val bounds = 10..1000
        assertEquals(1000, stepUp(1000, bounds))
        assertEquals(10, stepDown(10, bounds))
    }

    @Test
    fun `snap hits a preset inside tolerance and misses outside it`() {
        assertEquals(ScalePreset.Original, snapToPreset(100, PANEL, LANDSCAPE))
        assertEquals(ScalePreset.Fit, snapToPreset(200, PANEL, LANDSCAPE))
        assertEquals(ScalePreset.Fit, snapToPreset(205, PANEL, LANDSCAPE))
        assertEquals(ScalePreset.Fill, snapToPreset(400, PANEL, LANDSCAPE))
        assertNull(snapToPreset(206, PANEL, LANDSCAPE))
        assertNull(snapToPreset(150, PANEL, LANDSCAPE))
    }

    @Test
    fun `size mode keeps a preset when it lands on one`() {
        assertEquals(SizeMode.Preset(ScalePreset.Fit), sizeModeFor(200, PANEL, LANDSCAPE))
        assertEquals(SizeMode.Zoom(150), sizeModeFor(150, PANEL, LANDSCAPE))
    }

    @Test
    fun `slider fraction stays inside the track and rises with the percentage`() {
        val bounds = zoomBounds(PANEL, LANDSCAPE)
        assertEquals(0f, sliderFraction(bounds.first, bounds))
        assertEquals(1f, sliderFraction(bounds.last, bounds))
        var previous = -1f
        (bounds.first..bounds.last step 7).forEach { percent ->
            val fraction = sliderFraction(percent, bounds)
            assertTrue(fraction in 0f..1f, "$percent mapped outside the track: $fraction")
            assertTrue(fraction >= previous, "fraction fell at $percent")
            previous = fraction
        }
    }

    @Test
    fun `fraction round trips back to the percentage`() {
        val bounds = zoomBounds(PANEL, LANDSCAPE)
        listOf(20, 100, 200, 733, 2000).forEach { percent ->
            val back = percentFromFraction(sliderFraction(percent, bounds), bounds)
            assertTrue(back in (percent - 1)..(percent + 1), "$percent came back as $back")
        }
    }
}
