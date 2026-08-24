package com.woka.unit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.RasterStage
import com.woka.feature.editor.engine.image.fold
import com.woka.feature.editor.tools.crop.CropRect
import com.woka.feature.editor.tools.crop.CropTool
import com.woka.feature.editor.tools.markup.MarkupPoint
import com.woka.feature.editor.tools.markup.MarkupShape
import com.woka.feature.editor.tools.markup.MarkupTool
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
 * The markup tool in the fold (ME-113).
 *
 * **The claim worth a test is that the list is one stage.** Three arrows are three
 * entries and a single `RasterStage.Paint`, which is what keeps a hundred marks from
 * becoming a hundred stages to sort and map on every fold.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarkupToolTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val source = IntSize(1000, 800)

    private fun arrow(id: String) = MarkupShape.Arrow(
        id = id,
        color = 0xFFFF9F0AL,
        width = 6f,
        from = MarkupPoint(10f, 10f),
        to = MarkupPoint(200f, 150f)
    )

    private fun markupOp(vararg ids: String) = ToolOp(
        MarkupTool.manifest.id,
        MarkupTool.defaults().with(MarkupTool.Shapes, ids.map { arrow(it) })
    )

    private fun cropOp(rect: CropRect) =
        ToolOp(CropTool.manifest.id, ParamSet().with(CropTool.Rect, rect))

    /** Opening the tool and closing it again leaves the document exactly as it was. */
    @Test
    fun `an empty shape list composes no stage`() {
        assertNull(MarkupTool.stage(ParamSet()))
        assertNull(MarkupTool.stage(MarkupTool.defaults()))
    }

    @Test
    fun `three shapes compose exactly one paint stage`() {
        val op = markupOp("a", "b", "c")
        assertIs<RasterStage.Paint>(assertNotNull(MarkupTool.stage(op.params)))

        withFoldRegistry(listOf(MarkupTool)) { registry, _ ->
            val recipe = fold(EditDocument(ops = listOf(op)), registry, source)

            assertEquals(1, recipe.paints.size, "the list did not fold into one paint")
            assertEquals(source, recipe.outputSize, "a paint stage moved the frame")
            assertNull(recipe.colorFilter, "a paint stage produced a colour filter")
        }
    }

    /**
     * **The ordering both draw paths comment on and neither had ever run.** `RasterSurface`
     * and `renderAtNativeScale` apply the paint list *outside* the matrix precisely so a
     * crop clips an arrow rather than moving it — until now with nothing to prove it, since
     * `RasterStage.Paint` had existed since ME-007 with no producer.
     */
    @Test
    fun `a crop and markup keep the paint out of the matrix`() {
        withFoldRegistry(listOf(CropTool, MarkupTool)) { registry, _ ->
            val document = EditDocument(
                ops = listOf(markupOp("a"), cropOp(CropRect(100, 100, 640, 480)))
            )
            val recipe = fold(document, registry, source)

            assertEquals(1, recipe.paints.size, "the markup was lost or duplicated")
            // The matrix is the crop's translation and nothing else: the shapes are drawn
            // in source coordinates by the surface, after this has been applied.
            assertEquals(IntSize(640, 480), recipe.outputSize)
            assertEquals(Offset.Zero, recipe.matrix.map(Offset(100f, 100f)))
        }
    }

    /** `Single`: a second gesture replaces the list rather than stacking a second operation. */
    @Test
    fun `a second markup operation replaces the first`() {
        val committed = EditDocument(ops = listOf(markupOp("a")))
            .withPending(markupOp("a", "b"))
            .commitPending(MarkupTool.manifest.cardinality)

        assertEquals(1, committed.opsOf(MarkupTool.manifest.id).size)
        assertEquals(2, committed.ops.last().params[MarkupTool.Shapes].size)
    }
}
