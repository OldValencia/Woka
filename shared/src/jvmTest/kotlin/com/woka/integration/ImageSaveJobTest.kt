package com.woka.integration

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.ImageEditorEngine
import com.woka.feature.editor.engine.image.RasterStage
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_PNG
import com.woka.fixtures.STILL_WEBP
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
import kotlin.test.assertTrue

/**
 * `Done` on a bucket B file — the re-encode, which is what a format with no carrier
 * gets instead of a recipe (§5.1).
 *
 * Driven against `webp`, because that is the only format in the bucket today and the
 * bucket is expected to empty: when ME-108's RIFF carrier lands this file describes a
 * path nothing reaches, and that will be the moment to delete it rather than to widen
 * it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImageSaveJobTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val redder = StageTool("tint", ToolDomain.Color, RasterStage.Pixels(scaleRed(2f)))

    private fun onPicture(
        fixture: String,
        name: String,
        capability: EditCapability = EditCapability.Destructive,
        block: (File, ImageEditorEngine) -> Unit
    ) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-save").toFile()
        try {
            val file = File(dir, name)
            file.writeBytes(SampleMedia.file(fixture).readBytes())
            val registry = EditorToolRegistry(listOf(redder), SettingsRepositoryImpl())
            block(file, ImageEditorEngine(file, 0, registry, capability))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun onWebp(block: (File, ImageEditorEngine) -> Unit) =
        onPicture(STILL_WEBP, "photo.webp", block = block)

    private fun tinted() = EditDocument(ops = listOf(ToolOp("tint", redder.defaults())))

    /** Straight from the decoder: the path is the same before and after a save. */
    private fun File.pixels() = stillDecoderFor(this).frame(this, 0).toPixelMap().buffer

    @Test
    fun `an empty document writes nothing at all`() {
        onWebp { file, engine ->
            val original = file.readBytes()

            val result = runBlocking { engine.save(EditDocument()) }

            assertEquals(EditSaveResult.NothingToDo, result)
            assertContentEquals(original, file.readBytes())
        }
    }

    @Test
    fun `the save re-encodes in place and mints no second file`() {
        onWebp { file, engine ->
            val before = file.parentFile.list()!!.toList()
            val pixelsBefore = file.pixels()

            val result = runBlocking { engine.save(tinted()) }

            assertEquals(EditSaveResult.Saved(file.absolutePath), result)
            assertEquals(before, file.parentFile.list()!!.toList(), "a file was minted")
            // The pixels are where the edit went, because there is nowhere else for it.
            assertFalse(pixelsBefore.contentEquals(file.pixels()), "the picture is unchanged")
            assertFalse(EditRecipe.carries(file), "a recipe was written to bucket B")
        }
    }

    @Test
    fun `a source changed on disk stops the save and touches nothing`() {
        onWebp { file, engine ->
            Thread.sleep(5)
            file.writeBytes(SampleMedia.file(STILL_WEBP).readBytes() + byteArrayOf(0))
            val afterTamper = file.readBytes()

            val result = runBlocking { engine.save(tinted()) }

            assertTrue(result is EditSaveResult.Failed, "got $result")
            assertContentEquals(afterTamper, file.readBytes(), "the source was written over")
        }
    }

    @Test
    fun `a file that cannot be edited at all is refused rather than half-written`() {
        onPicture(STILL_PNG, "picture.bmp", EditCapability.Unavailable) { file, engine ->
            val original = file.readBytes()

            val result = runBlocking { engine.save(tinted()) }

            assertTrue(result is EditSaveResult.Failed, "got $result")
            assertEquals(listOf("picture.bmp"), file.parentFile.list()!!.toList())
            assertContentEquals(original, file.readBytes())
        }
    }

    @Test
    fun `a colour operation leaves the frame size alone`() {
        // `drawnSize` follows the recipe's output, so this is the case where it must
        // *not* move — a crop is what changes it, and there is no crop tool yet.
        onWebp { _, engine ->
            assertEquals(IntSize(640, 480), engine.sourceSize)
            assertEquals(IntSize(640, 480), engine.drawnSize(tinted()))
        }
    }
}
