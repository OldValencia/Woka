package com.woka.integration

import androidx.compose.ui.graphics.toPixelMap
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.IntValue
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.EditorEngineFactoryImpl
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.tools.transform.TransformTool
import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.fixtures.MULTIPAGE_TIFF
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.TIFF_PAGE_COUNT
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TIFF against a file with more than one page (ME-139).
 *
 * `TiffRecipeTest` proves the arithmetic on headers built by hand; this proves the thing only a real
 * multi-page file can: that repointing the header at a **rebuilt IFD0** leaves every page reachable
 * and every pixel where it was. Get the chain wrong and a three-page scan becomes a one-page scan.
 */
class TiffCarrierTest {

    private fun onCopy(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("woka-tiff").toFile()
        try {
            val file = File(dir, MULTIPAGE_TIFF)
            file.writeBytes(SampleMedia.file(MULTIPAGE_TIFF).readBytes())
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun document() =
        EditDocument(ops = listOf(ToolOp("transform", ParamSet(mapOf("angle" to IntValue(90))))))

    private fun File.pixelsOf(page: Int) =
        stillDecoderFor(this).frame(this, page).toPixelMap().buffer

    private fun File.pages() = stillDecoderFor(this).frameSet(this).frames.size

    @Test
    fun `a document round-trips through a multi-page tiff`() = onCopy { file ->
        assertTrue(EditRecipe.write(file, document()).isSuccess)

        assertEquals(document(), EditRecipe.documentIn(file, 0))
        assertTrue(EditRecipe.carries(file))
    }

    @Test
    fun `every page survives the rebuilt IFD0`() = onCopy { file ->
        val first = file.pixelsOf(0)
        val last = file.pixelsOf(TIFF_PAGE_COUNT - 1)

        assertTrue(EditRecipe.write(file, document()).isSuccess)

        assertEquals(TIFF_PAGE_COUNT, file.pages(), "the page chain was broken")
        assertContentEquals(first, file.pixelsOf(0))
        assertContentEquals(last, file.pixelsOf(TIFF_PAGE_COUNT - 1))
    }

    /** ME-107's promise, on the carrier that has to move a pointer to keep it. */
    @Test
    fun `clearing gives back a byte-identical file`() = onCopy { file ->
        val original = file.readBytes()
        EditRecipe.write(file, document())

        assertTrue(EditRecipe.clear(file).isSuccess)

        assertContentEquals(original, file.readBytes())
        assertNull(EditRecipe.documentIn(file, 0))
        assertFalse(EditRecipe.carries(file))
    }

    @Test
    fun `both spellings of the extension are editable`() = onCopy { file ->
        val factory = EditorEngineFactoryImpl(
            EditorToolRegistry(listOf(TransformTool), SettingsRepositoryImpl())
        )
        val tif = File(file.parentFile, "page.tif")
        tif.writeBytes(file.readBytes())

        assertEquals(EditCapability.Recorded, factory.capability(file.absolutePath))
        assertEquals(EditCapability.Recorded, factory.capability(tif.absolutePath))
    }
}
