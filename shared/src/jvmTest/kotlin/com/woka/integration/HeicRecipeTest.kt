package com.woka.integration

import androidx.compose.ui.graphics.toPixelMap
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.core.ui.image.ImageBitmapCache
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.EditorEngineFactoryImpl
import com.woka.feature.editor.engine.image.ImageEditorEngine
import com.woka.feature.editor.engine.image.RecipeStillEdits
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.tools.transform.TransformTool
import com.woka.feature.viewer.image.ImageLoader
import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.LIVE_PHOTO_STILL
import com.woka.fixtures.STILL_AVIF
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The formats a phone actually produces, editable for the first time (ME-108).
 *
 * Driven against the committed corpus rather than boxes built by hand, because the
 * claim is about **files a camera wrote**: a real `iloc`, real item offsets, and a
 * `uuid` box appended after all of it.
 *
 * The pixel assertion is the point of the whole model — these are formats Skia cannot
 * encode at all, so if the bytes of the picture moved, nothing could put them back.
 */
class HeicRecipeTest {

    private val registry = EditorToolRegistry(listOf(TransformTool), SettingsRepositoryImpl())

    private fun quarterTurn() = EditDocument(
        ops = listOf(
            ToolOp("transform", TransformTool.defaults().with(TransformTool.RotateRight, true))
        )
    )

    private fun onCopy(fixture: String, block: (File) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-isobmff").toFile()
        try {
            AppliedStillEdits.install(RecipeStillEdits(registry))
            val file = File(dir, fixture)
            file.writeBytes(SampleMedia.file(fixture).readBytes())
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** `size + 'free' + zeros` — the skippable box the format provides for padding. */
    private fun freeBox(size: Int): ByteArray {
        val total = size + 8
        return byteArrayOf(
            ((total shr 24) and 0xFF).toByte(),
            ((total shr 16) and 0xFF).toByte(),
            ((total shr 8) and 0xFF).toByte(),
            (total and 0xFF).toByte()
        ) + "free".toByteArray(Charsets.US_ASCII) + ByteArray(size)
    }

    private fun File.pixels() = stillDecoderFor(this).frame(this, 0).toPixelMap().buffer

    @Test
    fun `a document round-trips through a heic`() = onCopy(LIVE_PHOTO_STILL) { file ->
        assertTrue(EditRecipe.write(file, quarterTurn()).isSuccess)

        assertEquals(quarterTurn(), EditRecipe.documentIn(file, 0))
        assertTrue(EditRecipe.carries(file))
    }

    @Test
    fun `a document round-trips through an avif`() = onCopy(STILL_AVIF) { file ->
        assertTrue(EditRecipe.write(file, quarterTurn()).isSuccess)

        assertEquals(quarterTurn(), EditRecipe.documentIn(file, 0))
    }

    /**
     * Found from the tail, which is the whole reason the read window moved.
     *
     * The corpus fixture is a couple of kilobytes — a head read would find a box in it
     * however the window worked — so the file is grown past the 512 KB head window
     * first, with a `free` box: legal ISOBMFF, ignored by every decoder, and appended
     * rather than inserted so no item offset moves.
     */
    @Test
    fun `a photograph larger than the head window still answers`() =
        onCopy(LIVE_PHOTO_STILL) { file ->
            file.appendBytes(freeBox(600 * 1024))
            assertTrue(file.length() > 512 * 1024, "the padding did not grow the file")

            assertTrue(EditRecipe.write(file, quarterTurn()).isSuccess)

            assertTrue(EditRecipe.carries(file), "the box was written where nothing looks")
            assertEquals(quarterTurn(), EditRecipe.documentIn(file, 0))
        }

    /** The claim §5 rests on: writing a recipe is not an edit to the picture. */
    @Test
    fun `writing a recipe leaves a heic decoding identically`() = onCopy(LIVE_PHOTO_STILL) { file ->
        val before = file.pixels()

        EditRecipe.write(file, quarterTurn())

        assertContentEquals(before, file.pixels())
    }

    @Test
    fun `clearing returns the file to exactly what it was`() = onCopy(STILL_AVIF) { file ->
        val original = file.readBytes()
        EditRecipe.write(file, quarterTurn())

        assertTrue(EditRecipe.clear(file).isSuccess)

        assertContentEquals(original, file.readBytes())
        assertNull(EditRecipe.documentIn(file, 0))
        assertFalse(EditRecipe.carries(file))
    }

    /** What the user meets: the menu item stops being greyed out. */
    @Test
    fun `the factory calls a heic recorded rather than unavailable`() =
        onCopy(LIVE_PHOTO_STILL) { file ->
            val factory = EditorEngineFactoryImpl(registry)

            assertEquals(EditCapability.Recorded, factory.capability(file.absolutePath))
        }

    /**
     * End to end on a format with no encoder anywhere in the path: the editor saves,
     * and a reader draws the picture turned.
     */
    @Test
    fun `a heic edited through the engine comes back turned`() = onCopy(LIVE_PHOTO_STILL) { file ->
        val before = ImageLoader.load(file)
        val engine = ImageEditorEngine(file, 0, registry, EditCapability.Recorded)

        val result = runBlocking { engine.save(quarterTurn()) }

        assertTrue(result is EditSaveResult.Saved)
        ImageBitmapCache.remove(ImageBitmapCache.key(file.absolutePath, 0))
        val after = ImageLoader.load(file)
        assertEquals(before.height, after.width, "the axes did not swap")
        assertEquals(before.width, after.height)
    }
}
