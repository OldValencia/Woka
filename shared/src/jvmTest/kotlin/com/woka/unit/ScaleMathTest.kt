package com.woka.unit

import androidx.compose.ui.unit.IntSize
import com.woka.core.data.ScalePreset
import com.woka.feature.viewer.shell.SizeMode
import com.woka.feature.viewer.shell.computeGraphicsScale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Panel 1000×1000 over 500×250: the Fit image is already drawn at `native * 2`,
 * so every expectation below is the multiplier applied *on top of* that.
 */
private val PANEL = IntSize(1000, 1000)
private val LANDSCAPE = IntSize(500, 250)

class ScaleMathTest {

    @Test
    fun `fit is the identity because the image already carries it`() {
        val scale = computeGraphicsScale(SizeMode.Preset(ScalePreset.Fit), PANEL, LANDSCAPE)
        assertEquals(1f, scale)
    }

    @Test
    fun `fill is the ratio of the two axes`() {
        val scale = computeGraphicsScale(SizeMode.Preset(ScalePreset.Fill), PANEL, LANDSCAPE)
        assertEquals(2f, scale)
    }

    @Test
    fun `actual undoes the fit scale exactly`() {
        val scale = computeGraphicsScale(SizeMode.Preset(ScalePreset.Original), PANEL, LANDSCAPE)
        assertEquals(0.5f, scale)
    }

    @Test
    fun `a free zoom at the preset percentage lands on the preset`() {
        val preset = computeGraphicsScale(SizeMode.Preset(ScalePreset.Original), PANEL, LANDSCAPE)
        assertEquals(preset, computeGraphicsScale(SizeMode.Zoom(100), PANEL, LANDSCAPE))
        val fit = computeGraphicsScale(SizeMode.Preset(ScalePreset.Fit), PANEL, LANDSCAPE)
        assertEquals(fit, computeGraphicsScale(SizeMode.Zoom(200), PANEL, LANDSCAPE))
    }

    @Test
    fun `a free zoom is proportional to the percentage`() {
        assertEquals(2f, computeGraphicsScale(SizeMode.Zoom(400), PANEL, LANDSCAPE))
        assertEquals(0.25f, computeGraphicsScale(SizeMode.Zoom(50), PANEL, LANDSCAPE))
    }

    @Test
    fun `degenerate sizes give a neutral scale rather than a division by zero`() {
        val zoom = SizeMode.Zoom(400)
        assertEquals(1f, computeGraphicsScale(zoom, IntSize.Zero, LANDSCAPE))
        assertEquals(1f, computeGraphicsScale(zoom, PANEL, IntSize(0, 250)))
        assertEquals(1f, computeGraphicsScale(zoom, PANEL, IntSize(500, 0)))
    }
}
