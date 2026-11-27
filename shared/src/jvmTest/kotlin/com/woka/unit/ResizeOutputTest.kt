package com.woka.unit

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.fold
import com.woka.feature.editor.tools.crop.CropRect
import com.woka.feature.editor.tools.crop.CropTool
import com.woka.feature.editor.tools.resize.ResizeTool
import com.woka.feature.editor.tools.resize.SizeChoice
import com.woka.feature.editor.tools.resize.resizeOpFor
import com.woka.feature.editor.tools.resize.savedSize
import com.woka.feature.editor.tools.resize.sizedFor
import com.woka.support.MainDispatcherRule
import com.woka.support.withFoldRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Explicit pixels in the Resize op (RD-401): `Custom` in `Save your changes?`, and a copy's
 * own size standing in for the document's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResizeOutputTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val source = IntSize(1000, 800)

    private fun document(vararg choices: SizeChoice) = EditDocument(ops = choices.mapNotNull(::resizeOpFor))

    @Test
    fun `custom pixels come out exactly, each axis on its own`() {
        withFoldRegistry(listOf(ResizeTool)) { registry, _ ->
            val recipe = fold(document(SizeChoice.Custom(300, 500, keepProportions = false)), registry, source)

            assertEquals(IntSize(300, 500), recipe.outputSize)
        }
    }

    /** The pixels are of the frame the crop left, which only the fold knows (`Framed`). */
    @Test
    fun `custom pixels scale the cropped frame, not the source`() {
        withFoldRegistry(listOf(CropTool, ResizeTool)) { registry, _ ->
            val crop = ToolOp(CropTool.manifest.id, CropTool.defaults().with(CropTool.Rect, CropRect(0, 0, 400, 400)))
            val sized = EditDocument(ops = listOf(crop) + document(SizeChoice.Custom(200, 100, false)).ops)
            val recipe = fold(sized, registry, source)

            assertEquals(IntSize(200, 100), recipe.outputSize)
            // The crop's 400 × 400 squeezed to 200 × 100: half across, a quarter down.
            assertEquals(0.5f, recipe.matrix.values[0], 1e-4f)
            assertEquals(0.25f, recipe.matrix.values[5], 1e-4f)
        }
    }

    @Test
    fun `every choice reads back as itself`() {
        listOf(
            SizeChoice.Percent(50f),
            SizeChoice.Percent(37f),
            SizeChoice.Custom(1200, 900, keepProportions = true),
            SizeChoice.Custom(1200, 400, keepProportions = false)
        ).forEach { choice ->
            assertEquals(choice, document(choice).savedSize())
        }
        assertEquals(SizeChoice.Original, EditDocument().savedSize())
        assertNull(resizeOpFor(SizeChoice.Original), "Original saved a Resize")
    }

    /** A copy's size is its own: the saved 50 % goes, and `Original` leaves no Resize at all. */
    @Test
    fun `a copy's size replaces the document's`() {
        withFoldRegistry(listOf(ResizeTool)) { registry, _ ->
            val saved = document(SizeChoice.Percent(50f))

            assertEquals(IntSize(640, 360), fold(saved.sizedFor(IntSize(640, 360)), registry, source).outputSize)
            assertEquals(source, fold(saved.sizedFor(null), registry, source).outputSize)
            assertEquals(1, saved.sizedFor(IntSize(10, 10)).opsOf(ResizeTool.manifest.id).size)
        }
    }
}
