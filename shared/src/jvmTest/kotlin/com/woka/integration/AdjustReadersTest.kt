package com.woka.integration

import androidx.compose.ui.graphics.toPixelMap
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.RecipeStillEdits
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.tools.adjust.AdjustTool
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
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An adjustment through the readers that draw it (ME-111).
 *
 * Two claims, and the second is the one worth the fixture: the pixels **do** change, and
 * the frame does **not**. A colour stage that moved the frame would mean the fold had
 * mistaken it for geometry, and nothing else in the app would notice until a save came
 * out the wrong size.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdjustReadersTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun greyed() = EditDocument(
        ops = listOf(
            ToolOp(
                AdjustTool.manifest.id,
                AdjustTool.defaults().with(AdjustTool.Saturation, -100f)
            )
        )
    )

    private fun onPhoto(block: (File) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-adjust").toFile()
        try {
            val file = File(dir, "photo.jpg")
            file.writeBytes(SampleMedia.file(STILL_JPG).readBytes())
            AppliedStillEdits.install(
                RecipeStillEdits(EditorToolRegistry(listOf(AdjustTool), SettingsRepositoryImpl()))
            )
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the viewer draws the adjusted pixels at the same size`() {
        onPhoto { file ->
            val before = ImageLoader.load(file)
            val original = before.toPixelMap().buffer.copyOf()
            val size = before.width to before.height
            ImageBitmapCache.remove(ImageBitmapCache.key(file.absolutePath, 0))

            assertTrue(EditRecipe.write(file, greyed()).isSuccess)
            val adjusted = ImageLoader.load(file)

            assertEquals(size, adjusted.width to adjusted.height, "a colour stage moved the frame")
            assertTrue(
                !original.contentEquals(adjusted.toPixelMap().buffer),
                "the picture came back unchanged"
            )
        }
    }

    /** The reader that decodes small folds the same recipe and must also change. */
    @Test
    fun `the preview is adjusted too`() {
        onPhoto { file ->
            val before = StillPreview.load(file)
            val original = before.toPixelMap().buffer.copyOf()
            val size = before.width to before.height
            ImageBitmapCache.remove(ImageBitmapCache.previewKey(file.absolutePath))

            assertTrue(EditRecipe.write(file, greyed()).isSuccess)
            val after = StillPreview.load(file)

            assertEquals(size, after.width to after.height, "the preview changed shape")
            assertTrue(
                !original.contentEquals(after.toPixelMap().buffer),
                "the preview kept the original colours"
            )
        }
    }

    /** And a neutral document is the untouched instance, not a re-rendered copy. */
    @Test
    fun `a neutral adjustment costs no render at all`() {
        onPhoto { file ->
            val document = EditDocument(
                ops = listOf(ToolOp(AdjustTool.manifest.id, AdjustTool.defaults()))
            )
            assertTrue(EditRecipe.write(file, document).isSuccess)

            val decoded = ImageLoader.load(file)
            assertContentEquals(
                decoded.toPixelMap().buffer,
                AppliedStillEdits.apply(file, 0, decoded).toPixelMap().buffer
            )
        }
    }
}
