package com.woka.unit

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.RasterStage
import com.woka.feature.editor.engine.image.fold
import com.woka.feature.editor.tools.adjust.AdjustTool
import com.woka.feature.editor.tools.filter.FilterTool
import com.woka.feature.editor.tools.filter.PhotoFilter
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
import kotlin.test.assertTrue

/**
 * The filter tool in the fold (ME-112).
 *
 * **The claim worth a test is the collapse.** A look and a correction are both colour
 * matrices, so two tools produce **one** `ColorFilter` between them — which is what
 * `multiply` has existed for since ME-007 and what keeps a filter as cheap as a slider.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FilterToolTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val source = IntSize(640, 480)

    private fun filtered(filter: PhotoFilter, intensity: Float = 100f) = ToolOp(
        FilterTool.manifest.id,
        FilterTool.defaults().with(FilterTool.Filter, filter).with(FilterTool.Intensity, intensity)
    )

    private fun adjusted(amount: Float) = ToolOp(
        AdjustTool.manifest.id,
        AdjustTool.defaults().with(AdjustTool.Contrast, amount)
    )

    @Test
    fun `Original and zero intensity compose no stage`() {
        assertNull(FilterTool.stage(ParamSet()))
        assertNull(FilterTool.stage(FilterTool.defaults()))
        assertNull(FilterTool.stage(filtered(PhotoFilter.Noir, intensity = 0f).params))
    }

    @Test
    fun `a chosen look composes a pixels stage`() {
        assertIs<RasterStage.Pixels>(assertNotNull(FilterTool.stage(filtered(PhotoFilter.Vivid).params)))
    }

    /** Two colour tools, one filter — the whole reason colour stages multiply. */
    @Test
    fun `a filter and an adjustment collapse into one colour filter`() {
        withFoldRegistry(listOf(AdjustTool, FilterTool)) { registry, _ ->
            val document = EditDocument(ops = listOf(adjusted(40f), filtered(PhotoFilter.Dramatic)))
            val recipe = fold(document, registry, source)

            assertNotNull(recipe.colorFilter, "the pair produced no filter")
            // One field, so one filter — and nothing else: no paint pass and no
            // resize came out of two colour matrices.
            assertTrue(recipe.paints.isEmpty(), "a colour tool produced a paint pass")
            assertEquals(source, recipe.outputSize, "a colour stage moved the frame")
        }
    }

    /** And in flight it is still one: pending replaces the tool's own committed op. */
    @Test
    fun `a look being chosen previews as the look that will be committed`() {
        withFoldRegistry(listOf(FilterTool)) { registry, _ ->
            val dragging = EditDocument(
                ops = listOf(filtered(PhotoFilter.Noir)),
                pending = filtered(PhotoFilter.Vivid)
            )
            val committed = EditDocument(ops = listOf(filtered(PhotoFilter.Vivid)))

            assertEquals(
                fold(committed, registry, source).colorFilter,
                fold(dragging, registry, source).colorFilter,
                "the strip previewed a blend of both looks"
            )
        }
    }
}
