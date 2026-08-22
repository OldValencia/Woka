package com.woka.unit

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.RasterStage
import com.woka.feature.editor.engine.image.fold
import com.woka.feature.editor.tools.adjust.AdjustTool
import com.woka.feature.editor.tools.transform.TransformTool
import com.woka.support.MainDispatcherRule
import com.woka.support.withFoldRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The adjustments tool, and what the fold does with it (ME-111).
 *
 * **The claim being tested is the collapse.** Six sliders and any number of colour
 * tools become *one* `ColorFilter`; that is what `multiply` has existed for since
 * ME-007 and what makes a slider at 60 fps cost a filter rebuild and no pixel work.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdjustToolTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val source = IntSize(640, 480)

    private fun adjusted(vararg values: Pair<FloatParam, Float>): ToolOp {
        var params = AdjustTool.defaults()
        values.forEach { (spec, amount) -> params = params.with(spec, amount) }
        return ToolOp(AdjustTool.manifest.id, params)
    }

    @Test
    fun `a neutral panel composes no stage at all`() {
        assertNull(AdjustTool.stage(ParamSet()))
        assertNull(AdjustTool.stage(AdjustTool.defaults()))
    }

    @Test
    fun `one moved slider composes a pixels stage`() {
        val stage = AdjustTool.stage(adjusted(AdjustTool.Contrast to 40f).params)

        assertIs<RasterStage.Pixels>(assertNotNull(stage))
    }

    /** Two adjustments are one product, not two stages the canvas would apply in turn. */
    @Test
    fun `six adjustments collapse into one colour filter`() {
        withFoldRegistry(listOf(AdjustTool)) { registry, _ ->
            val op = adjusted(AdjustTool.Exposure to 20f, AdjustTool.Saturation to -30f, AdjustTool.Warmth to 15f)
            val recipe = fold(EditDocument(ops = listOf(op)), registry, source)

            assertNotNull(recipe.colorFilter, "the adjustments produced no filter")
            assertEquals(source, recipe.outputSize, "a colour stage moved the frame")
        }
    }

    @Test
    fun `a neutral document leaves the filter null`() {
        withFoldRegistry(listOf(AdjustTool)) { registry, _ ->
            val recipe = fold(
                EditDocument(ops = listOf(ToolOp(AdjustTool.manifest.id, AdjustTool.defaults()))),
                registry,
                source
            )

            assertNull(recipe.colorFilter, "an untouched panel still cost a filter")
        }
    }

    /** Colour is its own domain: a turn and an adjustment do not interfere. */
    @Test
    fun `an adjustment survives a geometry stage`() {
        withFoldRegistry(listOf(TransformTool, AdjustTool)) { registry, _ ->
            val turn = ToolOp(
                TransformTool.manifest.id,
                ParamSet().with(TransformTool.RotateRight, true)
            )
            val recipe = fold(
                EditDocument(ops = listOf(adjusted(AdjustTool.Contrast to 50f), turn)),
                registry,
                source
            )

            assertNotNull(recipe.colorFilter, "the turn dropped the adjustment")
            assertEquals(IntSize(480, 640), recipe.outputSize, "the adjustment blocked the turn")
        }
    }
}
