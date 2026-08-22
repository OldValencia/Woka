package com.woka.unit

import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.fold
import com.woka.feature.editor.tools.crop.CropRect
import com.woka.feature.editor.tools.crop.CropTool
import com.woka.feature.editor.tools.resize.ResizeLongEdge
import com.woka.feature.editor.tools.resize.ResizePreset
import com.woka.feature.editor.tools.resize.ResizeTool
import com.woka.support.MainDispatcherRule
import com.woka.support.withFoldRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The resize stage, and where the fold puts it (ME-110).
 *
 * **`Output` is the claim being tested.** A resize must be the last thing that happens
 * before encoding however the user ordered their work, and it is the fold's domain rank
 * that makes that true — so a crop made *after* a resize still resizes the crop.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResizeToolTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val source = IntSize(1000, 800)

    private fun resizeOp(percent: Float) =
        ToolOp(ResizeTool.manifest.id, ResizeTool.defaults().with(ResizeTool.Scale, percent))

    private fun cropOp(rect: CropRect) =
        ToolOp(CropTool.manifest.id, CropTool.defaults().with(CropTool.Rect, rect))

    @Test
    fun `full size composes no stage at all`() {
        assertNull(ResizeTool.stage(ParamSet()))
        assertNull(ResizeTool.stage(ResizeTool.defaults()))
    }

    @Test
    fun `a half resize halves the derived frame`() {
        withFoldRegistry(listOf(ResizeTool)) { registry, _ ->
            val recipe = fold(EditDocument(ops = listOf(resizeOp(50f))), registry, source)

            assertEquals(IntSize(500, 400), recipe.outputSize)
        }
    }

    /** Domain rank, not click order: the crop happens first whatever the document says. */
    @Test
    fun `a resize applies to the cropped frame, whichever was done first`() {
        withFoldRegistry(listOf(CropTool, ResizeTool)) { registry, _ ->
            val crop = cropOp(CropRect(0, 0, 400, 300))
            val resize = resizeOp(50f)

            val cropFirst = fold(EditDocument(ops = listOf(crop, resize)), registry, source)
            val resizeFirst = fold(EditDocument(ops = listOf(resize, crop)), registry, source)

            assertEquals(IntSize(200, 150), cropFirst.outputSize)
            assertEquals(IntSize(200, 150), resizeFirst.outputSize, "the resize did not sort last")
        }
    }

    /** A preset needs no frame; a long edge does, and is resolved before it is stored. */
    @Test
    fun `a preset and a long edge both resolve to a scale`() {
        val fromPreset = ResizeTool.resolve(
            ResizeTool.Preset,
            ResizeTool.defaults().with(ResizeTool.Preset, ResizePreset.Quarter),
            source
        )
        assertEquals(25f, fromPreset?.get(ResizeTool.Scale))

        val fromEdge = ResizeTool.resolve(
            ResizeTool.LongEdge,
            ResizeTool.defaults().with(ResizeTool.LongEdge, ResizeLongEdge.W1280),
            IntSize(4000, 3000)
        )
        val scale = fromEdge?.get(ResizeTool.Scale) ?: 0f
        assertTrue(abs(scale - 32f) < 0.01f, "1280 of 4000 is 32 %, got $scale")
    }

    /** The slider itself resolves to nothing: it is already the value it sets. */
    @Test
    fun `setting the scale directly rewrites nothing`() {
        assertNull(ResizeTool.resolve(ResizeTool.Scale, ResizeTool.defaults(), source))
    }

    @Test
    fun `the footnote follows the scale`() {
        val params = ResizeTool.defaults().with(ResizeTool.Scale, 50f)

        assertEquals("1000 × 800 → 500 × 400", ResizeTool.footnote(params, source))
        assertNull(ResizeTool.footnote(ResizeTool.defaults(), source))
    }
}
