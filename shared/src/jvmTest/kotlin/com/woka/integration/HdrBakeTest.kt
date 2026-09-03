package com.woka.integration

import androidx.compose.ui.graphics.toPixelMap
import com.woka.core.data.TransferKind
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditLayers
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.ImageEditorEngine
import com.woka.feature.editor.tools.hdr.bakesToSdr
import com.woka.feature.editor.tools.hdr.HdrOutput
import com.woka.feature.editor.tools.hdr.HdrTool
import com.woka.feature.viewer.image.displayable
import com.woka.feature.viewer.image.readStillColor
import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.fixtures.HDR_REAL_PQ_AVIF
import com.woka.fixtures.SampleMedia
import java.io.File
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `To SDR` — the one edit that rewrites the photograph (ME-118).
 *
 * The claim is not "a file was written". It is that **the file stops being HDR and keeps
 * looking the way the editor was drawing it**: the tag flips to SDR, and the pixels match
 * what `displayable` puts on the canvas, because since ME-177 that is already the
 * tone-mapped picture and baking is meant to be a no-op on what a person sees.
 */
class HdrBakeTest {

    private val dir: File = Files.createTempDirectory("woka-bake").toFile()

    @AfterTest
    fun clean() = dir.deleteRecursively().let { }

    private fun copyOfFixture(): File {
        val source = SampleMedia.file(HDR_REAL_PQ_AVIF)
        return File(dir, source.name).also { source.copyTo(it, overwrite = true) }
    }

    private val bake = EditDocument(
        listOf(ToolOp(HdrTool.manifest.id, ParamSet().with(HdrTool.Output, HdrOutput.ToSdr)))
    )

    private fun meanLuma(file: File): Float {
        val bitmap = displayable(file, stillDecoderFor(file).frame(file, 0))
        val pixels = bitmap.toPixelMap()
        var total = 0.0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val c = pixels[x, y]
                total += 0.2126 * c.red + 0.7152 * c.green + 0.0722 * c.blue
            }
        }
        return (total / (bitmap.width * bitmap.height) * 255f).toFloat()
    }

    @Test
    fun `the document carries the choice to the save`() {
        // `bakesToSdr` is what `writeFor` and the Done dialog both branch on, so it is
        // asserted directly rather than through either of them.
        assertTrue(bakesToSdr(bake))
        assertTrue(!bakesToSdr(EditDocument()))
    }

    @Test
    fun `the tool draws nothing, whichever option is chosen`() {
        // Both options look identical on the canvas: ME-177 tone maps at the decode, so
        // there is nothing for a raster stage to add. If this ever stops being true the
        // editor is doing the tone map twice.
        assertEquals(null, HdrTool.stage(ParamSet().with(HdrTool.Output, HdrOutput.ToSdr)))
        assertEquals(null, HdrTool.stage(ParamSet().with(HdrTool.Output, HdrOutput.Keep)))
    }

    @Test
    fun `a baked file is SDR and looks the same`() {
        val file = copyOfFixture()
        assertEquals(TransferKind.PQ, readStillColor(file).transfer, "the fixture stopped being PQ")
        val before = meanLuma(file)

        val result = withTempStorageRoot {
            val registry = EditorToolRegistry(emptyList(), SettingsRepositoryImpl())
            val engine = ImageEditorEngine(file, 0, registry, EditCapability.Recorded)
            runBlocking { engine.save(EditLayers(bake)) }
        }

        assertEquals(EditSaveResult.Saved(file.absolutePath), result)
        assertEquals(TransferKind.SDR, readStillColor(file).transfer, "the bake left it tagged HDR")
        val after = meanLuma(file)
        assertTrue(
            abs(after - before) < 4f,
            "the picture changed brightness when it should not have: $before -> $after"
        )
    }
}
