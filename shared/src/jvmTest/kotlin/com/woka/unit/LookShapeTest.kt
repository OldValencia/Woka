package com.woka.unit

import androidx.compose.ui.graphics.ColorMatrix
import com.woka.feature.editor.core.ParamPoint
import com.woka.feature.editor.tools.filter.FilterTool
import com.woka.feature.editor.tools.filter.PhotoFilter
import com.woka.feature.editor.tools.filter.filterMatrix
import com.woka.feature.editor.tools.filter.styleMatrix
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pad shapes the look, not the photograph (ME-150).
 *
 * **Every claim goes through `styleMatrix`**, because the ticket is about what a whole
 * style means: the axes are a factor of the look's own matrix and reach no pixel of
 * their own, which is what stops Styles being a second Exposure and a second Warmth
 * over the sliders `tools.adjust` already ships.
 *
 * Its own file rather than more of `FilterToolTest`, which is at the size limit.
 */
class LookShapeTest {

    private val corner = ParamPoint(100f, 100f)

    private fun style(filter: PhotoFilter, tone: ParamPoint, intensity: Float = 100f) =
        styleMatrix(
            FilterTool.defaults()
                .with(FilterTool.Filter, filter)
                .with(FilterTool.Intensity, intensity)
                .with(FilterTool.Tone, tone)
        )

    /** The ticket in one line: with no look to shape, the pad is furniture. */
    @Test
    fun `the pad is inert at Original`() {
        assertNull(style(PhotoFilter.Original, ParamPoint(0f, 0f)))
        assertNull(style(PhotoFilter.Original, corner), "the pad edited the photograph")
    }

    @Test
    fun `vividness at either end reshapes the look and empties it of colour`() {
        val plain = assertNotNull(style(PhotoFilter.Vivid, ParamPoint(0f, 0f))).values
        val vivid = assertNotNull(style(PhotoFilter.Vivid, ParamPoint(100f, 0f))).values
        assertTrue(!plain.contentEquals(vivid), "x at +100 left the look where it was")

        // Asserted against `setToSaturation`'s own answer rather than by eye: at -100
        // the look is itself with the colour taken out of it. Within a tolerance,
        // because the intensity lerp reaches full strength by arithmetic, not by
        // shortcut, and 1 + (v - 1) is not always v.
        val grey = ColorMatrix(assertNotNull(filterMatrix(PhotoFilter.Vivid)).values.copyOf())
        grey.timesAssign(ColorMatrix().apply { setToSaturation(0f) })
        val flat = assertNotNull(style(PhotoFilter.Vivid, ParamPoint(-100f, 0f))).values
        grey.values.forEachIndexed { at, expected ->
            assertTrue(abs(expected - flat[at]) < 1e-4f, "cell $at: ${flat[at]}, wanted $expected")
        }
    }

    @Test
    fun `brightness at the bottom lowers the look's offsets`() {
        val plain = assertNotNull(style(PhotoFilter.Vivid, ParamPoint(0f, 0f))).values
        val dimmed = assertNotNull(style(PhotoFilter.Vivid, ParamPoint(0f, -100f))).values
        listOf(4, 9, 14).forEach { at ->
            assertTrue(dimmed[at] < plain[at], "offset $at did not fall: ${dimmed[at]}")
        }
    }

    /** The words follow the meaning; the wire key does not, so ME-141's files open. */
    @Test
    fun `the pad names the look's shape and keeps its key`() {
        assertEquals("tone", FilterTool.Tone.key)
        assertEquals("Look shape", FilterTool.Tone.label)
        assertEquals("Vividness", FilterTool.Tone.xLabel)
        assertEquals("Brightness", FilterTool.Tone.yLabel)
    }

    /** A shape cannot outlive the look it shapes. */
    @Test
    fun `intensity at zero answers nothing with the pad at a corner`() {
        assertNull(style(PhotoFilter.Vivid, corner, intensity = 0f))
    }
}
