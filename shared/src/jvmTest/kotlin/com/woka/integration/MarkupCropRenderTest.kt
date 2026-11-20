package com.woka.integration

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.IntSize
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.engine.image.RasterStage
import com.woka.feature.editor.engine.image.fold
import com.woka.feature.editor.save.renderAtNativeScale
import com.woka.support.MainDispatcherRule
import com.woka.support.StageTool
import com.woka.support.toolOp
import com.woka.support.withFoldRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Markup goes through the crop with the picture (reported 2026-11-20). A mark is stored in
 * source pixels and its handles are placed through the recipe's matrix; the mark itself was
 * drawn outside that matrix, so after a crop off the top it sat lower than its own handles
 * by exactly the crop's corner — and on the saved file as well as on the canvas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarkupCropRenderTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val source = IntSize(100, 100)

    /** The bottom half: the picture's row 50 becomes the output's row 0. */
    private val crop = StageTool(
        "crop",
        ToolDomain.Geometry,
        RasterStage.Geometry(Matrix().apply { translate(0f, -50f) }, IntSize(100, 50))
    )

    /** A 10 px square at source (10, 60) — inside the kept half, at output (10, 10). */
    private val mark = StageTool(
        "markup",
        ToolDomain.Annotate,
        RasterStage.Paint { drawRect(Color.Red, topLeft = Offset(10f, 60f), size = Size(10f, 10f)) }
    )

    @Test
    fun `a crop keeps a mark on the part of the picture it was drawn on`() =
        withFoldRegistry(listOf(crop, mark)) { registry, _ ->
            val document = EditDocument(ops = listOf(toolOp("markup"), toolOp("crop")))
            val recipe = fold(document, registry, source)
            val pixels = renderAtNativeScale(ImageBitmap(source.width, source.height), source, recipe)
                .toComposeImageBitmap()
                .toPixelMap()

            assertEquals(Color.Red, pixels[15, 15], "the mark did not move with the crop")
            assertEquals(0f, pixels[15, 45].alpha, "the mark was drawn below where it belongs")
        }
}
