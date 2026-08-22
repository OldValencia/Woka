package com.woka.integration

import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.RecipeStillEdits
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.tools.resize.ResizeTool
import com.woka.feature.viewer.image.ImageLoader
import com.woka.feature.viewer.image.StillPreview
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_JPG
import com.woka.support.MainDispatcherRule
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A resize through the readers that draw it (ME-110).
 *
 * The proportion is stored, so both readers have to arrive at the same *shape* from very
 * different bitmaps: `ImageLoader` decodes at native size and `StillPreview` decodes
 * small. The preview is asserted by ratio rather than by pixels — its absolute size is
 * the preview cap's business, not the resize's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResizeReadersTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun halfSize() = EditDocument(
        ops = listOf(ToolOp(ResizeTool.manifest.id, ResizeTool.defaults().with(ResizeTool.Scale, 50f)))
    )

    private fun onPhoto(block: (File) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-resize").toFile()
        try {
            val file = File(dir, "photo.jpg")
            file.writeBytes(SampleMedia.file(STILL_JPG).readBytes())
            AppliedStillEdits.install(
                RecipeStillEdits(EditorToolRegistry(listOf(ResizeTool), SettingsRepositoryImpl()))
            )
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the viewer decodes a resized picture at half the source`() {
        onPhoto { file ->
            assertEquals(640, ImageLoader.load(file).width, "the fixture is not 640 wide")
            ImageBitmapCache.remove(ImageBitmapCache.key(file.absolutePath, 0))

            assertTrue(EditRecipe.write(file, halfSize()).isSuccess)
            val resized = ImageLoader.load(file)

            assertEquals(320, resized.width)
            assertEquals(240, resized.height)
        }
    }

    /**
     * The reader that decodes small. A scale folded twice — once by the decoder and once
     * by the recipe — is exactly what `RasterRecipe.scaledBy` exists to prevent, so the
     * preview must come back the same shape it went in.
     */
    @Test
    fun `the preview keeps its shape and is not scaled twice`() {
        onPhoto { file ->
            val before = StillPreview.load(file)
            val wanted = before.width.toFloat() / before.height
            ImageBitmapCache.remove(ImageBitmapCache.previewKey(file.absolutePath))

            assertTrue(EditRecipe.write(file, halfSize()).isSuccess)
            val after = StillPreview.load(file)

            val got = after.width.toFloat() / after.height
            assertTrue(abs(wanted - got) < 0.05f, "the preview's shape moved: $wanted to $got")
        }
    }
}
