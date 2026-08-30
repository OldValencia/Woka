package com.woka.integration

import androidx.compose.ui.graphics.toPixelMap
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.IntValue
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.ANIM_WEBP
import com.woka.fixtures.STILL_WEBP
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * WebP against files an encoder produced (ME-137).
 *
 * `WebpRecipeTest` proves the chunk arithmetic on bytes built by hand; this proves the
 * one thing only a real file can: that **promoting a simple file to the extended
 * container leaves the picture alone**. It is the single write in the app that changes
 * a photograph's own container, so the pixels are the assertion.
 */
class WebpCarrierTest {

    private fun onCopy(fixture: String, block: (File) -> Unit) {
        val dir = Files.createTempDirectory("woka-webp").toFile()
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

    /** Decoded straight from the decoder: `ImageLoader` caches on a path that does not move. */
    private fun File.pixels() = stillDecoderFor(this).frame(this, 0).toPixelMap().buffer

    /**
     * ME-137 promotes a simple file to the extended container on the way in, which is
     * the one write in the app that changes the picture's own container — so the pixels
     * are the assertion, not the round trip.
     */
    @Test
    fun `a document round-trips through a webp and the picture is untouched`() =
        onCopy(STILL_WEBP) { file ->
            val before = file.pixels()

            assertTrue(EditRecipe.write(file, document("transform")).isSuccess)

            assertEquals(document("transform"), EditRecipe.documentIn(file, 0))
            assertContentEquals(before, file.pixels())
        }

    /** Already extended, so nothing is promoted and every frame must survive it. */
    @Test
    fun `an animated webp keeps its frames`() = onCopy(ANIM_WEBP) { file ->
        val before = file.pixels()

        assertTrue(EditRecipe.write(file, document("transform")).isSuccess)

        assertEquals(document("transform"), EditRecipe.documentIn(file, 0))
        assertContentEquals(before, file.pixels())
        assertTrue(EditRecipe.clear(file).isSuccess)
        assertContentEquals(before, file.pixels())
    }
}
