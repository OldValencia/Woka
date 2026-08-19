package com.woka.integration

import androidx.compose.ui.graphics.toPixelMap
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.ImageEditorEngine
import com.woka.feature.editor.engine.image.RasterStage
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.viewer.image.ImageLoader
import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_JPG
import com.woka.support.MainDispatcherRule
import com.woka.support.StageTool
import com.woka.support.scaleRed
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `Done` on a bucket A file — the save §5 exists to make possible.
 *
 * Every assertion here is about something **not** happening: no second file, no
 * second recipe, no moved pixel. That is the whole claim of the model, and it is the
 * one the previous save model failed on its first hands-on use.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorSaveTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val redder = StageTool("tint", ToolDomain.Color, RasterStage.Pixels(scaleRed(2f)))

    private fun onPhoto(block: (File, ImageEditorEngine) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-done").toFile()
        try {
            val file = File(dir, "photo.jpg")
            file.writeBytes(SampleMedia.file(STILL_JPG).readBytes())
            val registry = EditorToolRegistry(listOf(redder), SettingsRepositoryImpl())
            block(file, ImageEditorEngine(file, 0, registry, EditCapability.Recorded))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun tinted() = EditDocument(ops = listOf(ToolOp("tint", redder.defaults())))

    /** Straight from the decoder: `ImageLoader` caches on the path, and the path is
     *  the same before and after a save. */
    private fun File.pixels() = stillDecoderFor(this).frame(this, 0).toPixelMap().buffer

    @Test
    fun `two saves leave one recipe, one file and the original pixels`() {
        onPhoto { file, engine ->
            val filesBefore = file.parentFile.list()!!.toList()
            val pixelsBefore = file.pixels()

            val first = runBlocking { engine.save(tinted()) }
            val afterOne = file.length()
            // The second save is the case the stale-source guard would wrongly refuse:
            // the file changed on disk, and it was this editor that changed it.
            val second = runBlocking { engine.save(tinted()) }

            assertTrue(first is EditSaveResult.Saved, "first save: $first")
            assertTrue(second is EditSaveResult.Saved, "second save: $second")
            assertEquals(filesBefore, file.parentFile.list()!!.toList(), "a file was minted")
            assertEquals(afterOne, file.length(), "a second recipe was appended")
            assertEquals(tinted(), EditRecipe.read(file))
            assertContentEquals(pixelsBefore, file.pixels(), "the pixels were rewritten")
        }
    }

    @Test
    fun `saving an emptied document removes the recipe`() {
        onPhoto { file, engine ->
            val original = file.readBytes()
            runBlocking { engine.save(tinted()) }
            assertTrue(EditRecipe.carries(file), "nothing was recorded to revert")

            val result = runBlocking { engine.save(EditDocument()) }

            assertTrue(result is EditSaveResult.Saved, "got $result")
            assertNull(EditRecipe.read(file))
            assertFalse(EditRecipe.carries(file))
            assertContentEquals(original, file.readBytes(), "the file is not as it was shot")
        }
    }

    @Test
    fun `a source changed on disk stops the save and touches nothing`() {
        onPhoto { file, engine ->
            // The engine stamped the file when it was built; this is somebody else
            // writing it while the editor was open.
            Thread.sleep(5)
            file.writeBytes(SampleMedia.file(STILL_JPG).readBytes() + byteArrayOf(0))
            val afterTamper = file.readBytes()

            val result = runBlocking { engine.save(tinted()) }

            assertTrue(result is EditSaveResult.Failed, "got $result")
            assertContentEquals(afterTamper, file.readBytes(), "the source was written over")
        }
    }

    @Test
    fun `every save drops exactly the cache entries for that path`() {
        onPhoto { file, engine ->
            val path = file.absolutePath
            val bitmap = ImageLoader.load(file)
            ImageBitmapCache[ImageBitmapCache.key(path, 0)] = bitmap
            ImageBitmapCache[ImageBitmapCache.previewKey(path)] = bitmap
            // A third entry, to prove the invalidation is targeted rather than a purge.
            val other = "somewhere/else.jpg"
            ImageBitmapCache[other] = bitmap

            runBlocking { engine.save(tinted()) }

            assertNull(ImageBitmapCache[ImageBitmapCache.key(path, 0)])
            assertNull(ImageBitmapCache[ImageBitmapCache.previewKey(path)])
            assertNotEquals(null, ImageBitmapCache[other], "an unrelated entry was evicted")
            ImageBitmapCache.remove(other)
        }
    }
}
