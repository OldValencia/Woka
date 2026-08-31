package com.woka.unit

import androidx.compose.ui.graphics.ColorMatrix
import com.woka.feature.editor.tools.adjust.blackPointMatrix
import com.woka.feature.editor.tools.adjust.brightnessMatrix
import com.woka.feature.editor.tools.adjust.contrastMatrix
import com.woka.feature.editor.tools.adjust.exposureMatrix
import com.woka.feature.editor.tools.adjust.saturationMatrix
import com.woka.feature.editor.tools.adjust.tintMatrix
import com.woka.feature.editor.tools.adjust.vibranceMatrix
import com.woka.feature.editor.tools.adjust.warmthMatrix
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * The eight adjustments as arithmetic (ME-111, ME-142), with no picture and no window.
 *
 * **Channels are 0..255 here**, because that is the scale a `ColorMatrix`'s fifth column
 * is added in. Asserting in 0..1 would make every offset look a hundred times too large
 * and is how contrast ends up pivoting on the wrong grey.
 */
class AdjustMatrixTest {

    /** One channel through a matrix: the row's three scales, plus its offset. */
    private fun ColorMatrix.applyTo(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        fun row(at: Int) =
            values[at] * r + values[at + 1] * g + values[at + 2] * b + values[at + 4]
        return Triple(row(0), row(5), row(10))
    }

    private fun assertNear(expected: Float, actual: Float, what: String) =
        assertTrue(abs(expected - actual) < 0.5f, "$what: expected $expected, got $actual")

    @Test
    fun `every adjustment is the identity at zero`() {
        val identity = ColorMatrix().values
        listOf(
            "exposure" to exposureMatrix(0f),
            "brightness" to brightnessMatrix(0f),
            "contrast" to contrastMatrix(0f),
            "blackPoint" to blackPointMatrix(0f),
            "saturation" to saturationMatrix(0f),
            "vibrance" to vibranceMatrix(0f),
            "warmth" to warmthMatrix(0f),
            "tint" to tintMatrix(0f)
        ).forEach { (name, matrix) ->
            assertContentEquals(identity, matrix.values, "$name at zero is not the identity")
        }
    }

    /**
     * **The one that is wrong by default.** Contrast pivots on mid grey, so grey stays
     * grey however far it is pushed; written as a bare multiply the whole picture
     * darkens as contrast is "increased".
     */
    @Test
    fun `contrast pivots on mid grey`() {
        listOf(-80f, -20f, 40f, 100f).forEach { amount ->
            val (r, _, _) = contrastMatrix(amount).applyTo(127.5f, 127.5f, 127.5f)
            assertNear(127.5f, r, "grey moved at contrast $amount")
        }
        // And it does separate the ends: white goes up, black goes down.
        val (light, _, _) = contrastMatrix(50f).applyTo(200f, 200f, 200f)
        val (dark, _, _) = contrastMatrix(50f).applyTo(50f, 50f, 50f)
        assertTrue(light > 200f && dark < 50f, "contrast did not spread the range")
    }

    /** Fully desaturated means the three channels answer the same number. */
    @Test
    fun `saturation at minus one hundred turns a colour grey`() {
        val (r, g, b) = saturationMatrix(-100f).applyTo(200f, 100f, 50f)

        assertNear(r, g, "red and green disagree")
        assertNear(g, b, "green and blue disagree")
    }

    /** Exposure is a multiply: one stop up doubles, one stop down halves. */
    @Test
    fun `exposure doubles and halves at the ends`() {
        val (up, _, _) = exposureMatrix(100f).applyTo(100f, 100f, 100f)
        val (down, _, _) = exposureMatrix(-100f).applyTo(100f, 100f, 100f)

        assertNear(200f, up, "a stop up did not double")
        assertNear(50f, down, "a stop down did not halve")
    }

    /** Brightness adds instead of scaling, which is what makes it not exposure. */
    @Test
    fun `brightness lifts every channel by the same amount`() {
        val (r, g, b) = brightnessMatrix(50f).applyTo(10f, 100f, 200f)

        assertNear(10f + 63.75f, r, "red")
        assertNear(100f + 63.75f, g, "green")
        assertNear(200f + 63.75f, b, "blue")
    }

    @Test
    fun `warmth moves red and blue in opposite directions`() {
        val (r, g, b) = warmthMatrix(100f).applyTo(100f, 100f, 100f)

        assertTrue(r > 100f, "warm did not raise red")
        assertTrue(b < 100f, "warm did not lower blue")
        assertNear(100f, g, "warmth touched green")
    }

    /** The other white-balance axis: green against magenta. */
    @Test
    fun `tint moves green against red and blue`() {
        val (r, g, b) = tintMatrix(100f).applyTo(100f, 100f, 100f)

        assertTrue(g > 100f, "tint did not raise green")
        assertTrue(r < 100f && b < 100f, "tint did not lower the magenta side")
    }
}
