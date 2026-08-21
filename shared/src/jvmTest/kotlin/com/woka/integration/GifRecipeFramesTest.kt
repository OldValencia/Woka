package com.woka.integration

import androidx.compose.ui.graphics.toPixelMap
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.IntValue
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.fixtures.ANIM_FRAME_COUNT
import com.woka.fixtures.ANIM_GIF
import com.woka.fixtures.SampleMedia
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A recipe going into a real animation and every frame of it coming back unchanged
 * (ME-138).
 *
 * **This is where a botched block walk shows up.** The unit tests build their own GIFs,
 * so they prove the surgery against the blocks it expects; an eight-frame file written
 * by a real encoder is what proves the walk found the right place to cut. Get the
 * insertion point wrong by a byte and the symptom is not a missing recipe — it is a
 * file whose frames stop decoding.
 *
 * Decoded through the decoder rather than `ImageLoader`, which caches on the path: the
 * path is the same before and after the write, so a cached bitmap would let this pass
 * without decoding anything.
 */
class GifRecipeFramesTest {

    private fun onCopy(fixture: String, block: (File) -> Unit) {
        val dir = Files.createTempDirectory("woka-gif-recipe").toFile()
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

    private fun File.frameCount() = stillDecoderFor(this).frameSet(this).frames.size

    private fun File.pixels(frameIndex: Int) =
        stillDecoderFor(this).frame(this, frameIndex).toPixelMap().buffer

    @Test
    fun `a document round-trips through an animated gif`() = onCopy(ANIM_GIF) { file ->
        assertTrue(EditRecipe.write(file, document("transform")).isSuccess)

        assertEquals(document("transform"), EditRecipe.read(file))
        assertTrue(EditRecipe.carries(file))
    }

    /**
     * The claim §5 rests on, asked of the case that can actually break it: not one
     * picture but eight, each built by the decoder from the ones before it.
     */
    @Test
    fun `every frame decodes identically after a recipe is written`() = onCopy(ANIM_GIF) { file ->
        val last = ANIM_FRAME_COUNT - 1
        assertEquals(ANIM_FRAME_COUNT, file.frameCount(), "the fixture is not what it was")
        val first = file.pixels(0)
        val end = file.pixels(last)

        EditRecipe.write(file, document("transform"))

        assertEquals(ANIM_FRAME_COUNT, file.frameCount(), "the write cost the file its frames")
        assertContentEquals(first, file.pixels(0))
        assertContentEquals(end, file.pixels(last))
    }

    /** The looping extension is what skia reads the repeat count out of. */
    @Test
    fun `the animation still declares its timings and its loop`() = onCopy(ANIM_GIF) { file ->
        val before = stillDecoderFor(file).frameSet(file)
        EditRecipe.write(file, document("transform"))
        val after = stillDecoderFor(file).frameSet(file)

        assertEquals(before.repeatCount, after.repeatCount, "the loop count was lost")
        assertEquals(
            before.frames.map { it.durationMs },
            after.frames.map { it.durationMs },
            "the frame timings were lost"
        )
    }

    @Test
    fun `clearing returns the animation to exactly what it was`() = onCopy(ANIM_GIF) { file ->
        val original = file.readBytes()
        EditRecipe.write(file, document("transform"))
        assertTrue(EditRecipe.clear(file).isSuccess)

        assertContentEquals(original, file.readBytes())
        assertNull(EditRecipe.read(file))
    }
}
