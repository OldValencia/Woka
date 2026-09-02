package com.woka.unit

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.LUMA_BUCKETS
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.SourceStats
import com.woka.feature.editor.tools.adjust.AdjustTool
import com.woka.feature.editor.tools.adjust.autoAdjustments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What Auto computes, and what it refuses to touch (ME-167).
 *
 * Built histograms rather than photographs: every assertion here is about the arithmetic
 * turning a shape into a slider, and a real picture would make each expected value a
 * constant nobody could check by reading. Whether the numbers *look* right on a
 * photograph is a judgement, and it belongs on screen.
 */
class AdjustAutoTest {

    /** A histogram with [count] pixels piled at [level], and nothing anywhere else. */
    private fun spike(level: Int, count: Int = 1000) =
        List(LUMA_BUCKETS) { if (it == level) count else 0 }

    private fun stats(
        luma: List<Int>,
        black: Float,
        white: Float,
        mean: Float
    ) = SourceStats(luma, black, white, mean)

    private val neutral = AdjustTool.defaults()

    /** A picture that needs nothing is left alone — every slider stays on its detent. */
    @Test
    fun `a well exposed picture is not corrected`() {
        val even = stats(spike(117), black = 0f, white = 0.9f, mean = 0.46f)
        val auto = autoAdjustments(even, neutral)

        assertEquals(0f, auto[AdjustTool.Exposure], 1f)
        assertEquals(0f, auto[AdjustTool.BlackPoint], 0.01f)
        assertEquals(0f, auto[AdjustTool.Shadows], 0.01f)
        assertEquals(0f, auto[AdjustTool.Highlights], 0.01f)
    }

    /** A dark picture is lifted, and by a positive exposure rather than by brightness. */
    @Test
    fun `a dark picture gains exposure`() {
        val dark = stats(spike(60), black = 0.1f, white = 0.4f, mean = 0.23f)
        assertTrue(autoAdjustments(dark, neutral)[AdjustTool.Exposure] > 10f)
    }

    /**
     * **The cap the naive version gets wrong.** A dark picture with its whites already at
     * the top must not be lifted until they are a hole — the headroom, not the mean,
     * decides how far Auto may go.
     */
    @Test
    fun `a dark picture with blown whites is barely lifted`() {
        val blown = stats(spike(30), black = 0f, white = 0.995f, mean = 0.12f)
        val lifted = autoAdjustments(blown, neutral)[AdjustTool.Exposure]
        assertTrue(lifted < 2f, "exposure $lifted ignored the headroom")
        assertTrue(lifted >= 0f, "exposure $lifted went the wrong way")
    }

    /** A raised foot is claimed back through Black Point, not through Brightness. */
    @Test
    fun `a washed out picture regains its black point`() {
        val washed = stats(spike(150), black = 0.25f, white = 0.95f, mean = 0.55f)
        assertTrue(autoAdjustments(washed, neutral)[AdjustTool.BlackPoint] > 30f)
    }

    /** Crushed shadows are lifted; clipped highlights are pulled down. */
    @Test
    fun `clipping at either end moves its own slider`() {
        val crushed = stats(spike(2), black = 0f, white = 0.5f, mean = 0.2f)
        assertTrue(autoAdjustments(crushed, neutral)[AdjustTool.Shadows] > 20f)

        val clipped = stats(spike(250), black = 0f, white = 1f, mean = 0.9f)
        assertTrue(autoAdjustments(clipped, neutral)[AdjustTool.Highlights] < -20f)
    }

    /**
     * **Idempotent, and it is the property most likely to be broken later.** Auto reads
     * the source's statistics, never the sliders, so a second press is the first press.
     */
    @Test
    fun `pressing auto twice gives what pressing it once gives`() {
        val picture = stats(spike(60), black = 0.1f, white = 0.4f, mean = 0.23f)
        val once = autoAdjustments(picture, neutral)
        assertEquals(once, autoAdjustments(picture, once))
    }

    /** Seven sliders are not Auto's to decide, and it must leave every one of them. */
    @Test
    fun `auto leaves colour, contrast and detail alone`() {
        val picture = stats(spike(60), black = 0.1f, white = 0.4f, mean = 0.23f)
        val touched = neutral
            .with(AdjustTool.Saturation, 40f)
            .with(AdjustTool.Contrast, -25f)
            .with(AdjustTool.Sharpness, 60f)
        val auto = autoAdjustments(picture, touched)

        assertEquals(40f, auto[AdjustTool.Saturation], 0.01f)
        assertEquals(-25f, auto[AdjustTool.Contrast], 0.01f)
        assertEquals(60f, auto[AdjustTool.Sharpness], 0.01f)
        assertEquals(0f, auto[AdjustTool.Warmth], 0.01f)
        assertEquals(0f, auto[AdjustTool.Noise], 0.01f)
    }

    /** With no picture measured yet there is nothing to compute from — see the tool. */
    @Test
    fun `an empty histogram changes nothing`() {
        val empty = stats(List(LUMA_BUCKETS) { 0 }, black = 0f, white = 0f, mean = 0f)
        assertEquals(ParamSet(), autoAdjustments(empty, ParamSet()))
    }

    /** And with nothing measured yet, Auto is a press that does nothing rather than a crash. */
    @Test
    fun `auto with no statistics resolves to nothing`() {
        assertEquals(null, AdjustTool.resolve(AdjustTool.Auto, neutral, IntSize.Zero, null))
    }
}
