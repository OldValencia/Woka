package com.woka.integration

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.woka.core.data.SettingsRepositoryImpl
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditLayers
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.ImageEditorEngine
import com.woka.feature.editor.engine.image.RecipeStillEdits
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.feature.editor.tools.transform.TransformTool
import com.woka.feature.viewer.image.stillDecoderFor
import com.woka.fixtures.BURST_FRAME_COUNT
import com.woka.fixtures.BURST_HEIC
import com.woka.fixtures.SampleMedia
import com.woka.support.withTempStorageRoot
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * One burst, five pictures, and an edit that reaches exactly one of them (ME-126).
 *
 * The ticket's real test. Driven through `ImageEditorEngine.save` and read back
 * through `AppliedStillEdits.apply` rather than through `EditPayload` directly,
 * because the claim is "the readers and the save agree about which frame is which" —
 * a unit test of the merge would not make it. A quarter turn is the edit throughout:
 * its result is visible as a pair of integers, so a frame that was not supposed to
 * move is caught by its own dimensions rather than by a pixel diff.
 */
class FrameRecipeTest {

    private val registry = EditorToolRegistry(listOf(TransformTool), SettingsRepositoryImpl())

    private fun quarterTurn() = EditDocument(
        ops = listOf(
            ToolOp("transform", TransformTool.defaults().with(TransformTool.RotateRight, true))
        )
    )

    /** A second, distinguishable edit — two turns rather than one. */
    private fun halfTurn() = EditDocument(ops = quarterTurn().ops + quarterTurn().ops)

    private fun onBurst(block: (File) -> Unit) = withTempStorageRoot {
        val dir = Files.createTempDirectory("woka-frames").toFile()
        try {
            AppliedStillEdits.install(RecipeStillEdits(registry))
            val file = File(dir, BURST_HEIC)
            file.writeBytes(SampleMedia.file(BURST_HEIC).readBytes())
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Straight from the decoder, never through `ImageLoader`: that caches on the path. */
    private fun decode(file: File, frameIndex: Int): ImageBitmap =
        stillDecoderFor(file).frame(file, frameIndex)

    private fun save(file: File, frameIndex: Int, document: EditDocument, scope: EditScope) {
        val engine = ImageEditorEngine(file, frameIndex, registry, EditCapability.Recorded)
        val layers = if (scope == EditScope.AllFrames) EditLayers(document)
        else EditLayers(EditDocument(), mapOf(frameIndex to document), setOf(frameIndex))
        val result = runBlocking { engine.save(layers) }
        assertTrue(result is EditSaveResult.Saved, "the save did not happen: $result")
    }

    /**
     * The property the whole ticket exists for. The four untouched frames come back as
     * the **same instance** they were decoded as — `StillEdits.apply` returns its
     * argument when there is nothing to fold — which is stronger than equal pixels.
     */
    @Test
    fun `a per-frame edit applies to its frame and to no other`() = onBurst { file ->
        save(file, 3, quarterTurn(), EditScope.SingleFrame)

        val edited = decode(file, 3)
        assertEquals(edited.height, AppliedStillEdits.apply(file, 3, edited).width)

        (0 until BURST_FRAME_COUNT).filter { it != 3 }.forEach { index ->
            val decoded = decode(file, index)
            assertSame(decoded, AppliedStillEdits.apply(file, index, decoded), "frame $index moved")
        }
    }

    /** The merge ME-134 fixed one level up, now at frame granularity. */
    @Test
    fun `a later sitting on another frame does not drop the first one`() = onBurst { file ->
        save(file, 3, quarterTurn(), EditScope.SingleFrame)
        save(file, 1, halfTurn(), EditScope.SingleFrame)

        val payload = EditRecipe.read(file)
        assertEquals(setOf(1, 3), payload?.frames?.keys, "a frame's edit was lost")
        assertEquals(1, payload?.frames?.get(3)?.ops?.size)
        assertEquals(2, payload?.frames?.get(1)?.ops?.size)
    }

    /** Keeps the keys since ME-175: an override adds to the base, it does not beat it. */
    @Test
    fun `an all-frames edit draws everywhere and the keys survive`() = onBurst { file ->
        save(file, 3, quarterTurn(), EditScope.SingleFrame)

        save(file, 0, quarterTurn(), EditScope.AllFrames)

        assertEquals(setOf(3), EditRecipe.read(file)?.frames?.keys, "frame 3's work was discarded")
        // Frame 3 turns twice, base and its own, so only its siblings measure one turn.
        ((0 until BURST_FRAME_COUNT) - 3).forEach { index ->
            val decoded = decode(file, index)
            assertEquals(
                decoded.height,
                AppliedStillEdits.apply(file, index, decoded).width,
                "frame $index did not draw the file's edit"
            )
        }
    }

    /**
     * Checked rather than assumed: a fixture of five identical pictures would make
     * this whole file pass while nothing worked.
     */
    @Test
    fun `the fixture really holds five different pictures`() = onBurst { file ->
        assertEquals(BURST_FRAME_COUNT, stillDecoderFor(file).frameSet(file).viewable.size)

        val first = decode(file, 0).toPixelMap().buffer
        assertFalse(first.contentEquals(decode(file, 4).toPixelMap().buffer))
    }
}
