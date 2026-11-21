package com.woka.integration

import androidx.compose.ui.graphics.toPixelMap
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditLayers
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.engine.image.ImageEditorEngine
import com.woka.feature.editor.engine.image.RenderPhoto
import com.woka.feature.editor.engine.image.fold
import com.woka.feature.editor.save.renderStill
import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RenderPhotoSafetyTest {
    private fun onTransparentPhoto(test: (File, EditorToolRegistry) -> Unit) = withTempStorageRoot {
        val directory = Files.createTempDirectory("woka-photo-safety").toFile()
        try {
            val source = File(directory, "transparent.gif")
            val image = BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(4, 4, 0xffff0000.toInt())
            assertTrue(ImageIO.write(image, "gif", source))
            test(source, EditorToolRegistry(emptyList(), SettingsRepositoryImpl()))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `unsupported encoders preserve transparency by exporting PNG`() {
        onTransparentPhoto { source, registry ->
            val engine = ImageEditorEngine(source, 0, registry, EditCapability.Recorded)
            val result = runBlocking { engine.export(RenderPhoto, EditLayers(EditDocument())) {} }
            assertTrue(result is EditSaveResult.Saved)
            val file = File(result.path)
            assertEquals("png", file.extension)
            val pixels = stillDecoderFor(file).frame(file, 0).toPixelMap()
            assertEquals(0f, pixels[0, 0].alpha)
            assertEquals(1f, pixels[4, 4].red)
        }
    }

    @Test
    fun `a changed source is refused without creating a render`() {
        onTransparentPhoto { source, registry ->
            val engine = ImageEditorEngine(source, 0, registry, EditCapability.Recorded)
            source.appendBytes(byteArrayOf(0))
            val before = source.readBytes()
            val result = runBlocking { engine.export(RenderPhoto, EditLayers(EditDocument())) {} }
            assertTrue(result is EditSaveResult.Failed)
            assertContentEquals(before, source.readBytes())
            assertEquals(listOf(source.name), source.parentFile.list()!!.toList())
        }
    }

    @Test
    fun `cancellation after writing removes only the newly created output`() {
        onTransparentPhoto { source, registry ->
            val size = stillDecoderFor(source).nativeSize(source, 0)
            val recipe = fold(EditDocument(), registry, size)
            val existing = File(source.parentFile, "transparent edited.png")
            existing.writeText("keep this file")
            val before = source.readBytes()
            var checks = 0
            assertFailsWith<CancellationException> {
                renderStill(source, 0, size, recipe) {
                    checks++
                    if (checks == 2) throw CancellationException("test cancellation")
                }
            }
            assertEquals(2, checks)
            assertEquals("keep this file", existing.readText())
            assertContentEquals(before, source.readBytes())
            assertEquals(2, source.parentFile.list()!!.size)
        }
    }
}
