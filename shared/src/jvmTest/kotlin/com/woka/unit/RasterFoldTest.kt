package com.woka.unit

import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.engine.image.RasterStage
import com.woka.feature.editor.engine.image.fold
import com.woka.support.FOLD_SOURCE
import com.woka.support.MainDispatcherRule
import com.woka.support.StageTool
import com.woka.support.scaleRed
import com.woka.support.toolOp
import com.woka.support.translation
import com.woka.support.withFoldRegistry
import com.woka.feature.editor.core.ToolDomain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RasterFoldTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `an op added last but ranked first applies first`() {
        // Annotate is clicked first, Geometry second; Geometry must still apply
        // first, or a crop would fail to clip an arrow drawn before it.
        val paint = StageTool("markup", ToolDomain.Annotate, RasterStage.Paint { })
        val geometry = StageTool(
            "crop",
            ToolDomain.Geometry,
            RasterStage.Geometry(translation(10f), IntSize(100, 50))
        )
        withFoldRegistry(listOf(paint, geometry)) { registry, _ ->
            val document = EditDocument(ops = listOf(toolOp("markup"), toolOp("crop")))
            val recipe = fold(document, registry, FOLD_SOURCE)

            assertEquals(IntSize(100, 50), recipe.outputSize)
            assertEquals(1, recipe.paints.size)
        }
    }

    @Test
    fun `two ops of the same domain keep insertion order`() {
        val first = StageTool("g1", ToolDomain.Geometry, RasterStage.Geometry(translation(1f), IntSize(1, 1)))
        val second = StageTool("g2", ToolDomain.Geometry, RasterStage.Geometry(translation(2f), IntSize(2, 2)))
        withFoldRegistry(listOf(first, second)) { registry, _ ->
            // The later of two equally ranked ops decides the output size, which is
            // only true if the sort is stable.
            val forward = fold(EditDocument(ops = listOf(toolOp("g1"), toolOp("g2"))), registry, FOLD_SOURCE)
            assertEquals(IntSize(2, 2), forward.outputSize)

            val reversed = fold(EditDocument(ops = listOf(toolOp("g2"), toolOp("g1"))), registry, FOLD_SOURCE)
            assertEquals(IntSize(1, 1), reversed.outputSize)
        }
    }

    @Test
    fun `two colour ops multiply into one filter`() {
        val a = StageTool("a", ToolDomain.Color, RasterStage.Pixels(scaleRed(2f)))
        val b = StageTool("b", ToolDomain.Color, RasterStage.Pixels(scaleRed(3f)))
        withFoldRegistry(listOf(a, b)) { registry, _ ->
            val recipe = fold(EditDocument(ops = listOf(toolOp("a"), toolOp("b"))), registry, FOLD_SOURCE)
            assertNotNull(recipe.colorFilter, "one filter for both, not a chain of two")
        }
    }

    @Test
    fun `an all-neutral colour op yields no filter at all`() {
        val neutral = StageTool("neutral", ToolDomain.Color, RasterStage.Pixels(ColorMatrix()))
        withFoldRegistry(listOf(neutral)) { registry, _ ->
            val recipe = fold(EditDocument(ops = listOf(toolOp("neutral"))), registry, FOLD_SOURCE)
            assertNull(recipe.colorFilter, "an identity filter still costs every pixel")
        }
    }

    @Test
    fun `an op from an unregistered tool is skipped and changes nothing`() {
        val known = StageTool("known", ToolDomain.Geometry, RasterStage.Geometry(translation(5f), IntSize(7, 7)))
        withFoldRegistry(listOf(known)) { registry, _ ->
            val document = EditDocument(ops = listOf(toolOp("ghost"), toolOp("known")))
            val recipe = fold(document, registry, FOLD_SOURCE)

            assertEquals(listOf("ghost"), recipe.skipped.map { it.toolId })
            assertEquals(IntSize(7, 7), recipe.outputSize)
        }
    }

    @Test
    fun `a disabled tool's op is skipped rather than lost`() {
        val known = StageTool("known", ToolDomain.Geometry, RasterStage.Geometry(translation(5f), IntSize(7, 7)))
        withFoldRegistry(listOf(known)) { registry, settings ->
            settings.setEditorToolEnabled("known", enabled = false)
            val recipe = fold(EditDocument(ops = listOf(toolOp("known"))), registry, FOLD_SOURCE)

            assertEquals(listOf("known"), recipe.skipped.map { it.toolId })
            assertEquals(FOLD_SOURCE, recipe.outputSize)
        }
    }

    @Test
    fun `outputSize follows the width and height swap on an odd quarter turn`() {
        val rotate = StageTool(
            "rotate",
            ToolDomain.Geometry,
            RasterStage.Geometry(
                Matrix().apply { rotateZ(90f) },
                IntSize(FOLD_SOURCE.height, FOLD_SOURCE.width)
            )
        )
        withFoldRegistry(listOf(rotate)) { registry, _ ->
            val recipe = fold(EditDocument(ops = listOf(toolOp("rotate"))), registry, FOLD_SOURCE)
            assertEquals(IntSize(480, 640), recipe.outputSize)
        }
    }

    @Test
    fun `a pending op folds in exactly like a committed one`() {
        val geometry = StageTool("crop", ToolDomain.Geometry, RasterStage.Geometry(translation(3f), IntSize(9, 9)))
        withFoldRegistry(listOf(geometry)) { registry, _ ->
            val pending = fold(EditDocument(pending = toolOp("crop")), registry, FOLD_SOURCE)
            val committed = fold(EditDocument(ops = listOf(toolOp("crop"))), registry, FOLD_SOURCE)
            assertEquals(committed.outputSize, pending.outputSize)
            assertEquals(committed.matrix.values.toList(), pending.matrix.values.toList())
        }
    }
}
