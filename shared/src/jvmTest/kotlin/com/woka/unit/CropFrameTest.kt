package com.woka.unit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.fold
import com.woka.feature.editor.engine.image.straightened
import com.woka.feature.editor.tools.crop.CropFrame
import com.woka.feature.editor.tools.crop.CropRect
import com.woka.feature.editor.tools.crop.CropTool
import com.woka.feature.editor.tools.crop.cropRectIn
import com.woka.feature.editor.tools.crop.scaledTo
import com.woka.feature.editor.tools.straighten.StraightenTool
import com.woka.support.FOLD_SOURCE
import com.woka.support.MainDispatcherRule
import com.woka.support.withFoldRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A crop chosen in one frame and kept in another (RD-205): a straighten changes the frame's
 * size with its angle, and the rect follows proportionally — drawn and cut alike.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CropFrameTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val rect = CropRect(left = 100, top = 60, width = 300, height = 200)

    private fun straighten(degrees: Float) =
        ToolOp(StraightenTool.manifest.id, ParamSet().with(StraightenTool.Angle, degrees))

    private fun crop(chosenIn: IntSize?) = ToolOp(
        CropTool.manifest.id,
        CropTool.defaults()
            .with(CropTool.Rect, rect)
            .with(CropTool.Frame, chosenIn?.let { CropFrame.of(it) } ?: CropFrame.Unset)
    )

    @Test
    fun `each edge is scaled along its own axis`() {
        val half = rect.scaledTo(IntSize(640, 480), IntSize(320, 240))
        assertEquals(CropRect(50, 30, 150, 100), half)
        assertEquals(rect, rect.scaledTo(IntSize(640, 480), IntSize(640, 480)))
    }

    /** The rectangle the overlay draws is the one the fold cuts, to the pixel. */
    @Test
    fun `the fold cuts exactly what the overlay draws after the angle changed`() {
        val chosenIn = straightened(FOLD_SOURCE, 5f).outputSize!!
        val stage = straightened(FOLD_SOURCE, 12f)
        val frame = stage.outputSize!!
        val document = EditDocument(ops = listOf(straighten(12f), crop(chosenIn)))
        val drawn = document.cropRectIn(frame)

        withFoldRegistry(listOf(CropTool, StraightenTool)) { registry, _ ->
            val recipe = fold(document, registry, FOLD_SOURCE)

            assertEquals(IntSize(drawn.width, drawn.height), recipe.outputSize)
            // The drawn rect's corner, taken back to the source, is the output's origin.
            val back = Matrix(stage.matrix.values.copyOf()).apply { invert() }
            val corner = recipe.matrix.map(back.map(Offset(drawn.left.toFloat(), drawn.top.toFloat())))
            assertEquals(0f, corner.x, 0.01f)
            assertEquals(0f, corner.y, 0.01f)
        }
        assertEquals(rect.scaledTo(chosenIn, frame), drawn)
    }

    @Test
    fun `a crop saved before RD-205 folds as it always did`() {
        withFoldRegistry(listOf(CropTool)) { registry, _ ->
            val recipe = fold(EditDocument(ops = listOf(crop(chosenIn = null))), registry, FOLD_SOURCE)

            assertEquals(IntSize(rect.width, rect.height), recipe.outputSize)
            assertEquals(Offset.Zero, recipe.matrix.map(Offset(100f, 60f)))
        }
    }
}
