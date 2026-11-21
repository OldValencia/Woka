package com.woka.integration

import androidx.compose.ui.graphics.toPixelMap
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditLayers
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.ImageEditorEngine
import com.woka.feature.editor.engine.image.RenderPhoto
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.fixtures.EXIF_ROTATED
import com.woka.fixtures.MULTIPAGE_TIFF
import com.woka.fixtures.STILL_AVIF
import com.woka.fixtures.STILL_JPG
import com.woka.fixtures.STILL_PNG
import com.woka.fixtures.SampleMedia
import com.woka.feature.editor.tools.transform.TransformTool
import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RenderPhotoTest {
    private val turn = EditDocument(listOf(
        ToolOp("transform", TransformTool.defaults().with(TransformTool.RotateRight, true))
    ))

    private fun onPhoto(fixture: String, frame: Int = 0, test: (File, ImageEditorEngine) -> Unit) =
        withTempStorageRoot {
            val directory = Files.createTempDirectory("woka-render-photo").toFile()
            try {
                val source = SampleMedia.file(fixture).copyTo(File(directory, fixture))
                val registry = EditorToolRegistry(listOf(TransformTool), SettingsRepositoryImpl())
                test(source, ImageEditorEngine(source, frame, registry, EditCapability.Recorded))
            } finally {
                directory.deleteRecursively()
            }
        }

    private fun ImageEditorEngine.render(document: EditDocument = turn): File = runBlocking {
        val result = export(RenderPhoto, EditLayers(document)) {}
        assertTrue(result is EditSaveResult.Saved, "$result")
        File(result.path)
    }

    @Test
    fun `render bakes unsaved edits and numbers copies without changing the source`() {
        onPhoto(STILL_JPG) { source, engine ->
            val before = source.readBytes()
            assertEquals(listOf(RenderPhoto), engine.exports)
            val first = engine.render()
            val second = engine.render()
            assertEquals("still edited.jpg", first.name)
            assertEquals("still edited 2.jpg", second.name)
            val pixels = stillDecoderFor(first).frame(first, 0)
            assertEquals(480 to 640, pixels.width to pixels.height)
            assertFalse(EditRecipe.carries(first))
            assertContentEquals(before, source.readBytes())
        }
    }

    @Test
    fun `lossless export rotates the actual source pixels`() {
        onPhoto(STILL_PNG) { source, engine ->
            val original = stillDecoderFor(source).frame(source, 0).toPixelMap()
            val output = engine.render()
            val actual = stillDecoderFor(output).frame(output, 0).toPixelMap()
            assertEquals("png", output.extension)
            for (y in 0 until actual.height step 31) {
                for (x in 0 until actual.width step 29) {
                    assertEquals(original[y, original.height - 1 - x], actual[x, y])
                }
            }
        }
    }

    @Test
    fun `a TIFF exports the requested page as JPEG`() {
        onPhoto(MULTIPAGE_TIFF, 1) { source, engine ->
            val original = stillDecoderFor(source).frame(source, 1)
            val output = engine.render()
            val decoded = stillDecoderFor(output).frame(output, 0)
            assertEquals("jpg", output.extension)
            assertEquals(original.height to original.width, decoded.width to decoded.height)
        }
    }

    @Test
    fun `an AVIF remains AVIF with baked edits`() {
        onPhoto(STILL_AVIF) { source, engine ->
            val original = stillDecoderFor(source).frame(source, 0)
            val output = engine.render()
            assertEquals("avif", output.extension)
            val decoded = stillDecoderFor(output).frame(output, 0)
            assertEquals(original.height to original.width, decoded.width to decoded.height)
            assertFalse(EditRecipe.carries(output))
        }
    }

    @Test
    fun `EXIF survives without applying the source orientation twice`() {
        onPhoto(EXIF_ROTATED) { _, engine ->
            val output = engine.render(EditDocument())
            assertTrue(output.readBytes().toString(Charsets.ISO_8859_1).contains("Exif"))
            val decoded = stillDecoderFor(output).frame(output, 0)
            assertEquals(480 to 640, decoded.width to decoded.height)
        }
    }
}
