package com.woka.unit

import androidx.compose.ui.unit.IntSize
import com.woka.core.data.ScalePreset
import com.woka.core.data.WokaSettings
import com.woka.feature.viewer.shell.SizeMode
import com.woka.feature.viewer.shell.percentIn
import com.woka.feature.viewer.shell.startSizeMode
import kotlin.test.Test
import kotlin.test.assertEquals

private val PANEL = IntSize(1000, 1000)
private val LANDSCAPE = IntSize(500, 250)

class SizeModeTest {

    @Test
    fun `a free default zoom opens at that percentage`() {
        val settings = WokaSettings(defaultUseZoom = true, defaultZoomPercent = 250)
        assertEquals(SizeMode.Zoom(250), startSizeMode(settings))
    }

    @Test
    fun `a preset default opens on the preset regardless of the stored percentage`() {
        val settings = WokaSettings(
            defaultUseZoom = false,
            defaultZoomPercent = 250,
            scalePreset = ScalePreset.Fill
        )
        assertEquals(SizeMode.Preset(ScalePreset.Fill), startSizeMode(settings))
    }

    @Test
    fun `the shipped default is Fit`() {
        assertEquals(SizeMode.Preset(ScalePreset.Fit), startSizeMode(WokaSettings()))
    }

    @Test
    fun `a nonsensical stored percentage cannot open at zero`() {
        val settings = WokaSettings(defaultUseZoom = true, defaultZoomPercent = 0)
        assertEquals(SizeMode.Zoom(1), startSizeMode(settings))
        val negative = WokaSettings(defaultUseZoom = true, defaultZoomPercent = -80)
        assertEquals(SizeMode.Zoom(1), startSizeMode(negative))
    }

    @Test
    fun `a free zoom reports itself and ignores the panel`() {
        assertEquals(150, SizeMode.Zoom(150).percentIn(PANEL, LANDSCAPE))
        assertEquals(150, SizeMode.Zoom(150).percentIn(IntSize.Zero, LANDSCAPE))
    }

    @Test
    fun `a preset re-resolves against the panel`() {
        val fit = SizeMode.Preset(ScalePreset.Fit)
        assertEquals(200, fit.percentIn(PANEL, LANDSCAPE))
        assertEquals(100, fit.percentIn(IntSize(500, 500), LANDSCAPE))
        assertEquals(400, SizeMode.Preset(ScalePreset.Fill).percentIn(PANEL, LANDSCAPE))
    }

    @Test
    fun `actual is the same percentage in every panel`() {
        val actual = SizeMode.Preset(ScalePreset.Original)
        assertEquals(SizeMode.ACTUAL_PERCENT, actual.percentIn(PANEL, LANDSCAPE))
        assertEquals(SizeMode.ACTUAL_PERCENT, actual.percentIn(IntSize(37, 91), LANDSCAPE))
    }
}
