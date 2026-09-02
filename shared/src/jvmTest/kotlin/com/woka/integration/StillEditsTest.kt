package com.woka.integration

import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditLayers
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.ImageEditorEngine
import com.woka.feature.editor.engine.image.RecipeStillEdits
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.tools.transform.TransformTool
import com.woka.feature.viewer.image.ImageLoader
import com.woka.feature.viewer.image.StillPreview
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_JPG
import com.woka.support.MainDispatcherRule
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The other half of the model: a file that carries an edit *looks* edited.
 *
 * ME-132 proved the recipe reaches the file. Between the two tickets the editor
 * appeared to do nothing at all, and these are the assertions that say it no longer
 * does — driven through the real readers rather than through `RecipeStillEdits`
 * directly, because "every reader applies it" is the claim and a unit test of the
 * implementation would not make it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StillEditsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /** A quarter turn: the one edit whose result is visible as a pair of integers. */
    private fun quarterTurn() =
        EditDocument(ops = listOf(ToolOp("transform", turnParams())))

    private fun turnParams() = TransformTool.defaults().with(TransformTool.RotateRight, true)

    private fun onPhoto(block: (File) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-edits").toFile()
        val installed = SettingsRepositoryImpl()
        try {
            val file = File(dir, "photo.jpg")
            file.writeBytes(SampleMedia.file(STILL_JPG).readBytes())
            AppliedStillEdits.install(
                RecipeStillEdits(EditorToolRegistry(listOf(TransformTool), installed))
            )
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a quarter turn in the file swaps the axes ImageLoader hands back`() {
        onPhoto { file ->
            assertEquals(640, ImageLoader.load(file).width, "the fixture is not 640 wide")
            ImageBitmapCache.remove(ImageBitmapCache.key(file.absolutePath, 0))

            assertTrue(EditRecipe.write(file, quarterTurn()).isSuccess)
            val edited = ImageLoader.load(file)

            assertEquals(480, edited.width)
            assertEquals(640, edited.height)
        }
    }

    @Test
    fun `clearing the recipe gives the original back`() {
        onPhoto { file ->
            EditRecipe.write(file, quarterTurn())
            ImageLoader.load(file)
            ImageBitmapCache.remove(ImageBitmapCache.key(file.absolutePath, 0))

            assertTrue(EditRecipe.clear(file).isSuccess)

            assertEquals(640, ImageLoader.load(file).width)
        }
    }

    /** Nothing pays a surface allocation to find out it has no edit. */
    @Test
    fun `a file with no recipe gets its own instance back`() {
        onPhoto { file ->
            val decoded = ImageLoader.load(file)
            assertSame(decoded, AppliedStillEdits.apply(file, 0, decoded))
            assertFalse(runBlocking { AppliedStillEdits.carries(file) })
        }
    }

    @Test
    fun `the carousel preview rebuilds after a write and shows the edit`() {
        onPhoto { file ->
            val before = StillPreview.load(file)
            assertTrue(before.width > before.height, "the fixture is not landscape")
            ImageBitmapCache.remove(ImageBitmapCache.previewKey(file.absolutePath))

            // The disk entry is keyed on path, size and mtime — a recipe write moves
            // two of the three, so the preview misses and rebuilds itself.
            assertTrue(EditRecipe.write(file, quarterTurn()).isSuccess)
            val after = StillPreview.load(file)

            assertTrue(after.height > after.width, "the preview kept the old orientation")
            assertTrue(runBlocking { AppliedStillEdits.carries(file) })
        }
    }

    @Test
    fun `an editor engine and the readers agree about the same file`() {
        onPhoto { file ->
            val registry = EditorToolRegistry(listOf(TransformTool), SettingsRepositoryImpl())
            val engine = ImageEditorEngine(file, 0, registry, EditCapability.Recorded)

            runBlocking { engine.save(EditLayers(quarterTurn())) }

            assertEquals(engine.drawnSize(quarterTurn()).width, ImageLoader.load(file).width)
            assertEquals(engine.drawnSize(quarterTurn()).height, ImageLoader.load(file).height)
        }
    }
}
