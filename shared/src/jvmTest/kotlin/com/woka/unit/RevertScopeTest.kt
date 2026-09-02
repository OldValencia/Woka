package com.woka.unit

import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditPayload
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.engine.image.revertRecipe
import com.woka.feature.editor.recipe.EditRecipe
import com.woka.fixtures.BURST_HEIC
import com.woka.fixtures.SampleMedia
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Revert removes what the scope switch is pointing at (ME-176).
 *
 * Against a real file rather than a payload, because the half worth proving is what the
 * *file* ends up carrying: an emptied payload must take the recipe with it, and only a
 * write can show that.
 */
class RevertScopeTest {

    private val warm = ToolOp("adjust", ParamSet())
    private val crop = ToolOp("crop", ParamSet())

    private fun write(file: File, payload: EditPayload) {
        EditRecipe.write(file, payload).getOrThrow()
    }

    /**
     * A copy per test, in a directory of its own: the recipe is written into the file
     * itself, so a shared fixture would carry one test's payload into the next.
     */
    private fun onCopy(fixture: String, block: (File) -> Unit) {
        val dir = Files.createTempDirectory("woka-revert").toFile()
        try {
            val file = File(dir, fixture)
            file.writeBytes(SampleMedia.file(fixture).readBytes())
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** A frame's own layer comes off; the base and the other frames go on drawing. */
    @Test
    fun `a single-frame revert leaves the base and the other frames`() = onCopy(BURST_HEIC) { file ->
        write(
            file,
            EditPayload(
                ops = listOf(warm),
                frames = mapOf(1 to EditDocument(listOf(crop)), 3 to EditDocument(listOf(crop))),
                layered = true
            )
        )

        assertIs<EditSaveResult.Saved>(revertRecipe(file, EditScope.SingleFrame, 3))

        val left = EditRecipe.read(file)
        assertEquals(listOf(warm), left?.ops, "the base went with the frame")
        assertEquals(setOf(1), left?.frames?.keys, "another frame's work was taken too")
    }

    /**
     * **The key goes rather than being emptied.** `frames[3] = EditDocument()` is a real
     * value under ME-175 and would leave the switch reading Single Frame for a frame with
     * nothing of its own.
     */
    @Test
    fun `a single-frame revert removes the key rather than emptying it`() = onCopy(BURST_HEIC) { file ->
        write(file, EditPayload(ops = listOf(warm), frames = mapOf(3 to EditDocument(listOf(crop))), layered = true))

        revertRecipe(file, EditScope.SingleFrame, 3)

        assertFalse(EditRecipe.read(file)?.frames?.containsKey(3) ?: false)
    }

    /** What is left may be nothing, and then the file stops carrying a recipe at all. */
    @Test
    fun `a single-frame revert that empties the payload removes the recipe`() = onCopy(BURST_HEIC) { file ->
        write(file, EditPayload(frames = mapOf(3 to EditDocument(listOf(crop))), layered = true))

        revertRecipe(file, EditScope.SingleFrame, 3)

        assertFalse(EditRecipe.carries(file), "an empty recipe was left behind")
    }

    /** Asked about a frame that carries nothing of its own, it does nothing at all. */
    @Test
    fun `a single-frame revert on an unkeyed frame is a no-op`() = onCopy(BURST_HEIC) { file ->
        write(file, EditPayload(ops = listOf(warm), layered = true))

        assertIs<EditSaveResult.NothingToDo>(revertRecipe(file, EditScope.SingleFrame, 2))
        assertTrue(EditRecipe.carries(file), "the base was removed by a frame's revert")
    }

    /** All Frames is what it always was: the whole recipe, keys included. */
    @Test
    fun `an all-frames revert removes everything`() = onCopy(BURST_HEIC) { file ->
        write(file, EditPayload(ops = listOf(warm), frames = mapOf(3 to EditDocument(listOf(crop))), layered = true))

        assertIs<EditSaveResult.Saved>(revertRecipe(file, EditScope.AllFrames, 3))

        assertFalse(EditRecipe.carries(file))
    }
}
