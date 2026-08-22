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
import com.woka.feature.editor.tools.transform.TransformTool
import com.woka.support.MainDispatcherRule
import com.woka.support.withFoldRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The crop tool's stage, and what the fold does with it (ME-109).
 *
 * A crop is **the one stage that names its own frame**, which is the whole reason
 * `RasterStage.Geometry.outputSize` is nullable: a rotation cannot know the frame it
 * produces and has it derived, while a crop chooses one and says so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CropToolTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val source = IntSize(1000, 800)

    private fun cropOp(rect: CropRect) =
        ToolOp(CropTool.manifest.id, ParamSet().with(CropTool.Rect, rect))

    private fun turnOp() =
        ToolOp(TransformTool.manifest.id, ParamSet().with(TransformTool.RotateRight, true))

    /** Opening the tool and closing it again must leave the document untouched. */
    @Test
    fun `an unchosen rect composes no stage at all`() {
        assertNull(CropTool.stage(ParamSet()))
        assertNull(CropTool.stage(ParamSet().with(CropTool.Rect, CropRect.Unset)))
    }

    @Test
    fun `a chosen rect names its own frame and moves its corner to the origin`() {
        val stage = assertIs<RasterStage.Geometry>(CropTool.stage(cropOp(CropRect(120, 60, 400, 300)).params))

        assertEquals(IntSize(400, 300), stage.outputSize)
        assertEquals(Offset.Zero, stage.matrix.map(Offset(120f, 60f)))
    }

    @Test
    fun `the fold reports the crop's frame rather than the source's`() {
        withFoldRegistry(listOf(CropTool)) { registry, _ ->
            val recipe = fold(EditDocument(ops = listOf(cropOp(CropRect(100, 100, 640, 480)))), registry, source)

            assertEquals(IntSize(640, 480), recipe.outputSize)
            assertEquals(Offset.Zero, recipe.matrix.map(Offset(100f, 100f)))
        }
    }

    /**
     * **Application order is the order the work was done in** (§3.4), not the rail's.
     * A crop made after a turn cuts the turned picture, so its rect is read against the
     * turned frame — 800×1000 here — and the result is the crop's own size.
     */
    @Test
    fun `a crop after a quarter turn clips the turned picture`() {
        withFoldRegistry(listOf(TransformTool, CropTool)) { registry, _ ->
            val document = EditDocument(ops = listOf(turnOp(), cropOp(CropRect(0, 0, 300, 500))))
            val recipe = fold(document, registry, source)

            assertEquals(IntSize(300, 500), recipe.outputSize, "the crop did not decide the frame")
        }
    }

    /** And the other way round: a turn after a crop turns what the crop kept. */
    @Test
    fun `a quarter turn after a crop turns the cropped frame`() {
        withFoldRegistry(listOf(TransformTool, CropTool)) { registry, _ ->
            val document = EditDocument(ops = listOf(cropOp(CropRect(0, 0, 300, 500)), turnOp()))
            val recipe = fold(document, registry, source)

            assertEquals(IntSize(500, 300), recipe.outputSize, "the turn did not follow the crop")
        }
    }

    /** `Single`: choosing a region twice is one region, not a crop inside a crop. */
    @Test
    fun `a second crop replaces the first`() {
        val committed = EditDocument(ops = listOf(cropOp(CropRect(0, 0, 300, 300))))
            .withPending(cropOp(CropRect(10, 10, 200, 200)))
            .commitPending(CropTool.manifest.cardinality)

        assertEquals(1, committed.opsOf(CropTool.manifest.id).size)
        assertEquals(CropRect(10, 10, 200, 200), committed.ops.last().params[CropTool.Rect])
    }
}
