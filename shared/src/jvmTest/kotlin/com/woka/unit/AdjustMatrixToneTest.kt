package com.woka.unit

import androidx.compose.ui.graphics.ColorMatrix
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.engine.image.multiply
import com.woka.feature.editor.tools.adjust.AdjustTool
import com.woka.feature.editor.tools.adjust.adjustmentMatrix
import com.woka.feature.editor.tools.adjust.blackPointMatrix
import com.woka.feature.editor.tools.adjust.saturationMatrix
import com.woka.feature.editor.tools.adjust.vibranceMatrix
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The two adjustments ME-142 added, and the one ordering rule they introduce.
 *
 * Channels are **0..255**, as `AdjustMatrixTest` records: the fifth column is added in
 * that scale, and asserting in 0..1 makes every offset look a hundred times too small.
 */
class AdjustMatrixToneTest {

    private fun ColorMatrix.applyTo(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        fun row(at: Int) =
            values[at] * r + values[at + 1] * g + values[at + 2] * b + values[at + 4]
        return Triple(row(0), row(5), row(10))
    }

    private fun assertNear(expected: Float, actual: Float, what: String) =
        assertTrue(abs(expected - actual) < 0.5f, "$what: expected $expected, got $actual")

    /**
     * The pin is the point: Black Point moves one end of the range and leaves the other
     * where it is, which is what separates it from Brightness.
     */
    @Test
    fun `black point crushes the shadows and leaves white where it was`() {
        val crushed = blackPointMatrix(100f)
        // 0.45 of the range is the ceiling, so that level is exactly what becomes black.
        val (dark, _, _) = crushed.applyTo(0.45f * 255f, 0.45f * 255f, 0.45f * 255f)
        assertNear(0f, dark, "the black point did not land on black")

        val (white, _, _) = crushed.applyTo(255f, 255f, 255f)
        assertNear(255f, white, "white moved")
    }

    @Test
    fun `a negative black point lifts the shadows and still leaves white alone`() {
        val lifted = blackPointMatrix(-100f)
        val (black, _, _) = lifted.applyTo(0f, 0f, 0f)
        assertTrue(black > 100f, "blacks were not lifted: $black")

        val (white, _, _) = lifted.applyTo(255f, 255f, 255f)
        assertNear(255f, white, "white moved")
    }

    /**
     * A slider that reverses direction anywhere is what a range-mapping bug produces,
     * and it is invisible until someone drags past the point where it turns.
     */
    @Test
    fun `black point moves one way across the whole span`() {
        val mid = 0.25f * 255f
        val samples = (-100..100 step 20).map { amount ->
            blackPointMatrix(amount.toFloat()).applyTo(mid, mid, mid).first
        }
        samples.zipWithNext { higher, lower ->
            assertTrue(lower < higher, "the span turned back on itself: $samples")
        }
    }

    /**
     * The whole of what "weighted" means, and the assertion that would catch this being
     * pasted from `saturationMatrix`: reds are spared, so they move less.
     */
    @Test
    fun `vibrance moves a desaturated blue further than a desaturated red`() {
        val vibrance = vibranceMatrix(100f)
        val grey = 128f
        val (redOut, _, _) = vibrance.applyTo(grey + 40f, grey, grey)
        val (_, _, blueOut) = vibrance.applyTo(grey, grey, grey + 40f)

        val redGain = redOut - (grey + 40f)
        val blueGain = blueOut - (grey + 40f)
        assertTrue(blueGain > redGain, "red gained $redGain, blue only $blueGain")
    }

    /**
     * **Black Point composes before the colour adjustments**, asserted on the product
     * rather than on the call order — the call order is what a later edit changes
     * silently, and a saturation re-scaled by the stretch would make the slider mean
     * something different at every black point.
     */
    @Test
    fun `black point is applied before saturation`() {
        val params = ParamSet()
            .with(AdjustTool.BlackPoint, 60f)
            .with(AdjustTool.Saturation, 60f)
        val folded =
            assertNotNull(adjustmentMatrix(params), "two moved adjustments folded to nothing")

        val correct = composed(blackPointMatrix(60f), saturationMatrix(60f))
        val reversed = composed(saturationMatrix(60f), blackPointMatrix(60f))
        assertTrue(
            !correct.values.contentEquals(reversed.values),
            "the two orders agree, so this test cannot tell them apart"
        )
        assertTrue(
            folded.values.contentEquals(correct.values),
            "folded in the wrong order:\n${folded.values.toList()}\n${correct.values.toList()}"
        )
    }

    /**
     * Composed through **the production helper**, not a second implementation of it.
     * `ColorMatrixMath.multiply` is where the "receiver acts first" convention lives
     * (§9, 2026-08-18), and a test that spelled it out again could agree with itself
     * while disagreeing with the fold.
     */
    private fun composed(first: ColorMatrix, second: ColorMatrix): ColorMatrix =
        multiply(multiply(null, first), second)
}
