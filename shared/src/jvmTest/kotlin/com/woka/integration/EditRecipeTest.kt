package com.woka.integration

import androidx.compose.ui.graphics.toPixelMap
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.IntValue
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_BMP
import com.woka.fixtures.STILL_JPG
import com.woka.fixtures.STILL_PNG
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A recipe going into a real picture and coming back out of it.
 *
 * Driven against the committed corpus rather than hand-built headers, because the
 * claim being tested is about **files a camera actually produces** — a JPEG whose
 * leading `APPn` run is not empty, a PNG with more than `IHDR` and `IDAT` in it.
 *
 * The pixel assertions are the point of the whole model. Everything else here could
 * pass while the feature was worthless; if the decoded pixels move, it is broken.
 */
class EditRecipeTest {

    private fun onCopy(fixture: String, block: (File) -> Unit) {
        val dir = Files.createTempDirectory("woka-recipe").toFile()
        try {
            val file = File(dir, fixture)
            file.writeBytes(SampleMedia.file(fixture).readBytes())
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun document(id: String) =
        EditDocument(ops = listOf(ToolOp(id, ParamSet(mapOf("angle" to IntValue(90))))))

    /**
     * Decoded straight from the decoder, **never** through `ImageLoader`: that
     * caches on the path, and the path is the same before and after a write, so a
     * cached bitmap would make this assertion pass without decoding anything.
     */
    private fun File.pixels() = stillDecoderFor(this).frame(this, 0).toPixelMap().buffer

    @Test
    fun `a document round-trips through a jpeg`() = onCopy(STILL_JPG) { file ->
        assertTrue(EditRecipe.write(file, document("transform")).isSuccess)
        assertEquals(document("transform"), EditRecipe.read(file))
        assertTrue(EditRecipe.carries(file))
    }

    @Test
    fun `a document round-trips through a png`() = onCopy(STILL_PNG) { file ->
        assertTrue(EditRecipe.write(file, document("transform")).isSuccess)
        assertEquals(document("transform"), EditRecipe.read(file))
    }

    /** The claim §5 rests on: writing a recipe is not an edit to the picture. */
    @Test
    fun `writing a recipe leaves the picture identical`() = onCopy(STILL_JPG) { file ->
        val before = file.pixels()
        EditRecipe.write(file, document("transform"))
        assertContentEquals(before, file.pixels())
    }

    @Test
    fun `writing a recipe leaves a png identical too`() = onCopy(STILL_PNG) { file ->
        val before = file.pixels()
        EditRecipe.write(file, document("transform"))
        assertContentEquals(before, file.pixels())
    }

    @Test
    fun `a second write replaces rather than accumulating`() = onCopy(STILL_JPG) { file ->
        EditRecipe.write(file, document("alpha"))
        val once = file.length()
        EditRecipe.write(file, document("gamma"))

        assertEquals(document("gamma"), EditRecipe.read(file))
        // One segment, not two: the same payload size, so the same file size.
        assertEquals(once, file.length())
    }

    @Test
    fun `clearing returns the file to exactly what it was`() = onCopy(STILL_PNG) { file ->
        val original = file.readBytes()
        EditRecipe.write(file, document("transform"))
        assertTrue(EditRecipe.clear(file).isSuccess)

        assertContentEquals(original, file.readBytes())
        assertNull(EditRecipe.read(file))
        assertFalse(EditRecipe.carries(file))
    }

    @Test
    fun `clearing a file that carries nothing does not write to it`() = onCopy(STILL_JPG) { file ->
        val stamp = file.lastModified()
        assertTrue(EditRecipe.clear(file).isSuccess)
        assertEquals(stamp, file.lastModified())
    }

    @Test
    fun `an untouched picture carries nothing`() = onCopy(STILL_JPG) { file ->
        assertNull(EditRecipe.read(file))
        assertFalse(EditRecipe.carries(file))
    }

    /**
     * `bmp` is the one format left with no slot at all — nothing to record an edit in
     * and no Skia encoder to rewrite the picture with, which is the whole of §5.1's
     * bucket C now that ME-138 has taken GIF.
     */
    @Test
    fun `a format with no carrier answers nothing rather than failing`() = onCopy(STILL_BMP) { file ->
        assertNull(EditRecipe.read(file))
        assertTrue(EditRecipe.write(file, document("transform")).isFailure)
        // Clearing is a success because there is nothing there to clear.
        assertTrue(EditRecipe.clear(file).isSuccess)
    }
}
