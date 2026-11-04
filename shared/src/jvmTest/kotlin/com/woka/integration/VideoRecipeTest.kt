package com.woka.integration

import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditLayers
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.IntValue
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.EditorEngineFactoryImpl
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.fixtures.LIVE_PHOTO_MOTION
import com.woka.fixtures.SampleMedia
import com.woka.probe.CLIP_FPS
import com.woka.probe.generateClip
import com.woka.support.episodeTwoFile
import com.woka.support.withFoldRegistry
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A video keeping an edit, end to end, on a real container (ME-288, ME-282, ME-283).
 *
 * `livephoto.mov` rather than a hand-built header: the claim is about a file something
 * actually produced, and this one opens `\0\0\0\x14ftypqt  ` — a QuickTime brand rather
 * than the `heic` the carrier was written for, which is the half of ME-288 that could have
 * been wrong.
 *
 * **The media data is never rewritten**, so the pixel assertion a still's test makes is
 * replaced here by a byte one: revert must give the file back exactly.
 */
class VideoRecipeTest {

    /**
     * Every container with a carrier: the `mov` ME-288 shipped, an `mkv` (ME-282) and an
     * `avi` (ME-283). The corpus has no `avi`, so one is encoded here — the claim is still
     * about a file a real muxer produced.
     */
    private fun onCopy(block: (File) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-video-recipe").toFile()
        try {
            listOf(LIVE_PHOTO_MOTION, episodeTwoFile().name).forEach { name ->
                File(dir, name).also { it.writeBytes(SampleMedia.file(name).readBytes()) }
            }
            generateClip(File(dir, "clip.avi"), "libopenh264", seconds = 1, gop = CLIP_FPS, format = "avi")
            dir.listFiles().orEmpty().sortedBy { it.name }.forEach { file -> block(file) }
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Two-digit angles, so both payloads encode to the same number of bytes. */
    private fun document(angle: Int) =
        EditDocument(ops = listOf(ToolOp("transform", ParamSet(mapOf("angle" to IntValue(angle))))))

    @Test
    fun `a document goes into each container and a second engine reads it back`() = onCopy { file ->
        withFoldRegistry(emptyList()) { registry, _ ->
            val factory = EditorEngineFactoryImpl(registry)
            val original = file.length()
            val engine = assertNotNull(factory.create(file.absolutePath))

            val saved = runBlocking { engine.save(EditLayers(document(90))) }

            assertIs<EditSaveResult.Saved>(saved, "${file.name} refused an edit")
            assertTrue(file.length() > original, "nothing was appended to ${file.name}")
            assertEquals(document(90).ops, assertNotNull(EditRecipe.read(file)).ops)
            // **The reopen the engine's `storedDocument` exists for**: without it the
            // editor comes back blank and the next `Done` prunes the recipe it wrote.
            val reopened = assertNotNull(factory.create(file.absolutePath))
            assertEquals(document(90).ops, reopened.storedDocument().ops)
        }
    }

    /** ME-280's in-place write, now reached by a video rather than only by a photograph. */
    @Test
    fun `a second save replaces the box rather than adding one`() = onCopy { file ->
        withFoldRegistry(emptyList()) { registry, _ ->
            val factory = EditorEngineFactoryImpl(registry)
            val first = assertNotNull(factory.create(file.absolutePath))
            runBlocking { first.save(EditLayers(document(90))) }
            val afterFirst = file.length()

            val second = assertNotNull(factory.create(file.absolutePath))
            val saved = runBlocking { second.save(EditLayers(document(45))) }

            assertIs<EditSaveResult.Saved>(saved)
            assertEquals(afterFirst, file.length(), "the old box was not truncated away in ${file.name}")
            assertEquals(document(45).ops, assertNotNull(EditRecipe.read(file)).ops)
        }
    }

    @Test
    fun `revert gives the file back byte for byte`() = onCopy { file ->
        withFoldRegistry(emptyList()) { registry, _ ->
            val factory = EditorEngineFactoryImpl(registry)
            val original = file.readBytes()
            val engine = assertNotNull(factory.create(file.absolutePath))
            runBlocking { engine.save(EditLayers(document(90))) }

            val reverted = runBlocking { engine.revert() }

            assertIs<EditSaveResult.Saved>(reverted, "revert had nothing to remove in ${file.name}")
            assertContentEquals(original, file.readBytes(), "${file.name} did not come back byte for byte")
        }
    }
}
