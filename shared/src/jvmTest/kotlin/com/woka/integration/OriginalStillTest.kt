package com.woka.integration

import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.RecipeStillEdits
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.tools.crop.CropRect
import com.woka.feature.editor.tools.crop.CropTool
import com.woka.feature.viewer.image.ImageLoader
import com.woka.fixtures.STILL_HEIGHT
import com.woka.fixtures.STILL_JPG
import com.woka.fixtures.STILL_WIDTH
import com.woka.fixtures.SampleMedia
import com.woka.support.MainDispatcherRule
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Original` reads the still as shot (RD-502): under a crop recipe the viewer's picture is the
 * kept region and the original is the whole frame, each from its own cache entry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OriginalStillTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val rect = CropRect(left = 160, top = 40, width = 200, height = 400)

    private fun onCroppedPhoto(block: (File) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-original").toFile()
        try {
            val file = File(dir, "photo.jpg")
            file.writeBytes(SampleMedia.file(STILL_JPG).readBytes())
            AppliedStillEdits.install(
                RecipeStillEdits(EditorToolRegistry(listOf(CropTool), SettingsRepositoryImpl()))
            )
            val crop = EditDocument(ops = listOf(ToolOp(CropTool.manifest.id, ParamSet().with(CropTool.Rect, rect))))
            assertTrue(EditRecipe.write(file, crop).isSuccess)
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the original is the whole frame while the viewer draws the crop`() {
        onCroppedPhoto { file ->
            val original = ImageLoader.loadOriginal(file)
            assertEquals(STILL_WIDTH to STILL_HEIGHT, original.width to original.height)

            val edited = ImageLoader.load(file)
            assertEquals(rect.width to rect.height, edited.width to edited.height)
            // The edited entry did not take the original's place, nor the other way round.
            val again = ImageLoader.loadOriginal(file)
            assertEquals(STILL_WIDTH, again.width)
        }
    }
}
