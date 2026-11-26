package com.woka.unit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.RasterRecipe
import com.woka.feature.editor.engine.image.fold
import com.woka.feature.editor.engine.image.straightened
import com.woka.feature.editor.tools.straighten.StraightenTool
import com.woka.feature.editor.tools.transform.TransformTool
import com.woka.support.FOLD_SOURCE
import com.woka.support.MainDispatcherRule
import com.woka.support.withFoldRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where a straighten folds (RD-205): before every turn and flip, whatever order they were
 * made in — the angle belongs to the original picture (the author, 2026-11-26).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StraightenFoldTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val tools = listOf(TransformTool, StraightenTool)
    private val level = straightened(FOLD_SOURCE, 10f).outputSize!!

    private fun straighten(degrees: Float) =
        ToolOp(StraightenTool.manifest.id, ParamSet().with(StraightenTool.Angle, degrees))

    private fun press(action: ActionParam) =
        ToolOp(TransformTool.manifest.id, ParamSet().with(action, true))

    private fun foldOf(vararg ops: ToolOp, block: (RasterRecipe) -> Unit) =
        withFoldRegistry(tools) { registry, _ -> block(fold(EditDocument(ops = ops.toList()), registry, FOLD_SOURCE)) }

    @Test
    fun `a straighten made after a quarter turn still folds before it`() {
        foldOf(press(TransformTool.RotateRight), straighten(10f)) { turnedFirst ->
            foldOf(straighten(10f), press(TransformTool.RotateRight)) { levelledFirst ->
                assertEquals(IntSize(level.height, level.width), turnedFirst.outputSize)
                assertSamePicture(levelledFirst, turnedFirst)
            }
        }
    }

    /** The mirrored picture stays level: the flip mirrors what the straighten produced. */
    @Test
    fun `a flip after levelling mirrors the level picture`() {
        val centreish = Offset(300f, 200f)
        val levelled = straightened(FOLD_SOURCE, 10f).matrix.map(centreish)
        foldOf(straighten(10f), press(TransformTool.FlipHorizontal)) { recipe ->
            assertEquals(level, recipe.outputSize)
            val at = recipe.matrix.map(centreish)
            assertEquals(level.width - levelled.x, at.x, 0.01f)
            assertEquals(levelled.y, at.y, 0.01f)
        }
    }

    @Test
    fun `a straighten with the tool switched off is skipped, not lost`() {
        withFoldRegistry(tools) { registry, settings ->
            settings.setEditorToolEnabled(StraightenTool.manifest.id, enabled = false)
            val recipe = fold(EditDocument(ops = listOf(straighten(10f))), registry, FOLD_SOURCE)

            assertEquals(listOf(StraightenTool.manifest.id), recipe.skipped.map { it.toolId })
            assertEquals(FOLD_SOURCE, recipe.outputSize)
        }
    }

    private fun assertSamePicture(expected: RasterRecipe, actual: RasterRecipe) {
        assertEquals(expected.outputSize, actual.outputSize)
        listOf(Offset(0f, 0f), Offset(320f, 240f), Offset(600f, 50f)).forEach { point ->
            val want = expected.matrix.map(point)
            val got = actual.matrix.map(point)
            assertEquals(want.x, got.x, 0.01f, "x of $point")
            assertEquals(want.y, got.y, 0.01f, "y of $point")
        }
    }
}
