package com.woka.integration

import com.woka.core.data.SettingsRepositoryImpl
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.ImageEditorEngine
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.tools.transform.TransformTool
import com.woka.fixtures.ANIM_GIF
import com.woka.fixtures.BURST_FRAME_COUNT
import com.woka.fixtures.BURST_HEIC
import com.woka.fixtures.MULTIPAGE_TIFF
import com.woka.fixtures.SampleMedia
import com.woka.fixtures.STILL_JPG
import com.woka.fixtures.TIFF_PAGE_COUNT
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the engine answers about a file's frames, and what a scoped `Done` leaves on
 * disk (ME-156).
 *
 * `EditorFrames` is what decides whether the switch is drawn at all, and it is read
 * from a real decoder here rather than constructed — the claim is about **files**, and
 * "a burst is five pictures and a GIF is one that moves" is exactly the distinction a
 * hand-built value would assume rather than test.
 */
class FrameScopeSaveTest {

    private val registry = EditorToolRegistry(listOf(TransformTool), SettingsRepositoryImpl())

    private fun quarterTurn() = EditDocument(
        ops = listOf(
            ToolOp("transform", TransformTool.defaults().with(TransformTool.RotateRight, true))
        )
    )

    private fun onCopy(fixture: String, block: (File) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-scope").toFile()
        try {
            val file = File(dir, fixture)
            file.writeBytes(SampleMedia.file(fixture).readBytes())
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun engineOn(file: File, frameIndex: Int = 0) =
        ImageEditorEngine(file, frameIndex, registry, EditCapability.Recorded)

    @Test
    fun `a this-frame save writes a key and leaves the file's own ops alone`() =
        onCopy(BURST_HEIC) { file ->
            runBlocking { engineOn(file, 3).save(quarterTurn(), EditScope.ThisFrame) }

            val payload = EditRecipe.read(file)
            assertEquals(setOf(3), payload?.frames?.keys)
            assertTrue(payload?.ops.isNullOrEmpty(), "an un-keyed edit appeared from nowhere")
        }

    /** A second sitting on the same frame continues per-frame rather than going back. */
    @Test
    fun `a reopened frame reports the scope it was written in`() = onCopy(BURST_HEIC) { file ->
        runBlocking { engineOn(file, 3).save(quarterTurn(), EditScope.ThisFrame) }

        assertEquals(EditScope.ThisFrame, engineOn(file, 3).frames.storedScope)
        assertEquals(EditScope.AllFrames, engineOn(file, 1).frames.storedScope)
        assertEquals(setOf(3), engineOn(file, 1).frames.overridden)
    }

    @Test
    fun `an all-frames save clears the keys`() = onCopy(BURST_HEIC) { file ->
        runBlocking { engineOn(file, 3).save(quarterTurn(), EditScope.ThisFrame) }

        runBlocking { engineOn(file, 0).save(quarterTurn(), EditScope.AllFrames) }

        assertTrue(EditRecipe.read(file)?.frames.isNullOrEmpty())
        assertEquals(1, EditRecipe.read(file)?.ops?.size)
    }

    @Test
    fun `a burst is a set of pictures the switch can mean something for`() =
        onCopy(BURST_HEIC) { file ->
            val frames = engineOn(file).frames

            assertEquals(BURST_FRAME_COUNT, frames.count)
            assertFalse(frames.playsAsAnimation)
            assertTrue(frames.allowsPerFrame)
        }

    @Test
    fun `a page chain is one too`() = onCopy(MULTIPAGE_TIFF) { file ->
        val frames = engineOn(file).frames

        assertEquals(TIFF_PAGE_COUNT, frames.count)
        assertTrue(frames.allowsPerFrame)
    }

    /** The two files that must show nothing: one picture, and one picture that moves. */
    @Test
    fun `a photograph and an animation are offered no choice`() {
        onCopy(STILL_JPG) { file ->
            assertEquals(1, engineOn(file).frames.count)
            assertFalse(engineOn(file).frames.allowsPerFrame)
        }
        onCopy(ANIM_GIF) { file ->
            val frames = engineOn(file).frames
            assertTrue(frames.playsAsAnimation, "the fixture does not report as animated")
            assertFalse(frames.allowsPerFrame)
        }
    }
}
