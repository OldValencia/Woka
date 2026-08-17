package com.woka.integration

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.IntSize
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.SaveTarget
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.ImageEditorEngine
import com.woka.feature.editor.engine.image.RasterStage
import com.woka.feature.viewer.image.ImageLoader
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_PNG
import com.woka.support.MainDispatcherRule
import com.woka.support.StageTool
import com.woka.support.scaleRed
import com.woka.support.withTempStorageRoot
import com.woka.core.data.SettingsRepositoryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A document becoming a file.
 *
 * Every case here is about **the source surviving**: that is what §5's whole design is
 * for, and the failure modes are the ones where it would not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImageSaveJobTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val redder = StageTool("tint", ToolDomain.Color, RasterStage.Pixels(scaleRed(2f)))

    private fun onCopiedSource(block: (File, ImageEditorEngine) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-save").toFile()
        try {
            val file = File(dir, "photo.png")
            file.writeBytes(SampleMedia.file(STILL_PNG).readBytes())
            val registry = EditorToolRegistry(listOf(redder), SettingsRepositoryImpl())
            block(file, ImageEditorEngine(file, 0, registry))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun tinted() = EditDocument(ops = listOf(ToolOp("tint", redder.defaults())))

    @Test
    fun `an empty document writes nothing at all`() {
        onCopiedSource { file, engine ->
            val before = file.parentFile.list()!!.size

            val result = runBlocking {
                engine.save(EditDocument(), SaveTarget.Copy("photo-edited.png"))
            }

            assertEquals(EditSaveResult.NothingToDo, result)
            assertEquals(before, file.parentFile.list()!!.size)
        }
    }

    @Test
    fun `a copy lands beside the source and leaves it byte-identical`() {
        onCopiedSource { file, engine ->
            val original = file.readBytes()

            val result = runBlocking {
                engine.save(tinted(), SaveTarget.Copy("photo-edited.png"))
            }

            assertTrue(result is EditSaveResult.Saved, "got $result")
            assertTrue(File(file.parentFile, "photo-edited.png").isFile)
            assertTrue(original.contentEquals(file.readBytes()), "the source was written to")
        }
    }

    @Test
    fun `the recipe actually reaches the pixels`() {
        onCopiedSource { file, engine ->
            runBlocking {
                engine.save(tinted(), SaveTarget.Copy("photo-edited.png"))
            }

            // Without this the whole suite would pass against a save that wrote the
            // source back unchanged.
            val source = ImageLoader.load(file).toPixelMap()
            val saved = ImageLoader.load(File(file.parentFile, "photo-edited.png")).toPixelMap()
            val differs = (0 until minOf(source.width, saved.width) step 17).any { x ->
                source[x, 0] != saved[x, 0]
            }
            assertTrue(differs, "the saved copy is pixel-identical to the source")
        }
    }

    @Test
    fun `a source changed on disk stops the save and touches nothing`() {
        onCopiedSource { file, engine ->
            // The engine stamped the file when it was built; this is somebody else
            // writing it while the editor was open.
            Thread.sleep(5)
            file.writeBytes(SampleMedia.file(STILL_PNG).readBytes() + byteArrayOf(0))
            val afterTamper = file.readBytes()

            val result = runBlocking {
                engine.save(tinted(), SaveTarget.Overwrite)
            }

            assertTrue(result is EditSaveResult.Failed, "got $result")
            assertTrue(afterTamper.contentEquals(file.readBytes()), "the source was overwritten")
        }
    }

    @Test
    fun `overwrite drops exactly the two cache entries for that path`() {
        onCopiedSource { file, engine ->
            val path = file.absolutePath
            val bitmap = ImageLoader.load(file)
            ImageBitmapCache[ImageBitmapCache.key(path, 0)] = bitmap
            ImageBitmapCache[ImageBitmapCache.previewKey(path)] = bitmap
            // A third entry, to prove the invalidation is targeted rather than a purge.
            val other = "somewhere/else.png"
            ImageBitmapCache[other] = bitmap

            runBlocking { engine.save(tinted(), SaveTarget.Overwrite) }

            assertEquals(null, ImageBitmapCache[ImageBitmapCache.key(path, 0)])
            assertEquals(null, ImageBitmapCache[ImageBitmapCache.previewKey(path)])
            assertNotEquals(null, ImageBitmapCache[other], "an unrelated entry was evicted")
            ImageBitmapCache.remove(other)
        }
    }

    @Test
    fun `a format Skia cannot encode is refused rather than half-written`() = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-save-bmp").toFile()
        try {
            val file = File(dir, "picture.bmp")
            file.writeBytes(SampleMedia.file(STILL_PNG).readBytes())
            val engine = ImageEditorEngine(
                file, 0, EditorToolRegistry(listOf(redder), SettingsRepositoryImpl())
            )

            val result = runBlocking {
                engine.save(tinted(), SaveTarget.Copy("picture-edited.bmp"))
            }

            assertTrue(result is EditSaveResult.Failed, "got $result")
            assertEquals(listOf("picture.bmp"), dir.list()!!.toList())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a colour operation leaves the frame size alone`() {
        // `drawnSize` follows the recipe's output, so this is the case where it must
        // *not* move — a crop is what changes it, and there is no crop tool yet.
        onCopiedSource { _, engine ->
            assertEquals(IntSize(640, 480), engine.sourceSize)
            assertEquals(IntSize(640, 480), engine.drawnSize(tinted()))
        }
    }
}
