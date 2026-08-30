package com.woka.unit

import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditPayload
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.IntValue
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.core.editorJson
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun op(id: String) = ToolOp(id, ParamSet(mapOf("angle" to IntValue(90))))

private fun document(id: String) = EditDocument(ops = listOf(op(id)))

/**
 * The keyed payload, as arithmetic (ME-126).
 *
 * ME-126 ships with nothing on screen that can write a key, so these assertions and
 * `FrameRecipeTest` are the whole deliverable. The two compatibility tests at the
 * bottom are the ones that matter most: they are what says a photograph edited before
 * the format changed still means what it meant.
 */
class EditPayloadTest {

    private val keyed = EditPayload(
        ops = listOf(op("transform")),
        frames = mapOf(3 to document("crop"))
    )

    @Test
    fun `a frame with no key of its own draws the file's ops`() {
        assertEquals(document("transform"), keyed.documentFor(1))
    }

    @Test
    fun `a frame with a key draws that document instead`() {
        assertEquals(document("crop"), keyed.documentFor(3))
    }

    @Test
    fun `the scope is per frame exactly where a key exists`() {
        assertEquals(EditScope.ThisFrame, keyed.scopeOf(3))
        assertEquals(EditScope.AllFrames, keyed.scopeOf(1))
    }

    @Test
    fun `saving for all frames drops every override`() {
        val next = keyed.with(document("adjust"), EditScope.AllFrames, 2)

        assertEquals(listOf(op("adjust")), next.ops)
        assertTrue(next.frames.isEmpty(), "an override survived an all-frames save")
    }

    @Test
    fun `saving for one frame leaves the file's ops and every other key alone`() {
        val next = keyed.with(document("adjust"), EditScope.ThisFrame, 2)

        assertEquals(listOf(op("transform")), next.ops)
        assertEquals(document("adjust"), next.frames[2])
        assertEquals(document("crop"), next.frames[3], "another frame's edit was dropped")
    }

    /** An operation in flight is a drag, not a record — it may not reach a file. */
    @Test
    fun `a pending operation never reaches the payload`() {
        val dragging = EditDocument(ops = listOf(op("transform")), pending = op("crop"))

        val next = EditPayload().with(dragging, EditScope.ThisFrame, 0)

        assertEquals(document("transform"), next.frames[0])
    }

    /**
     * `frames[3] = EditDocument()` is the only way to say *this frame alone is
     * unedited*, so it keeps its key — and a payload of nothing but such keys still
     * folds to nothing, which is what removes the recipe (§5.2).
     */
    @Test
    fun `a payload folding to nothing is empty and one carrying anything is not`() {
        assertTrue(EditPayload().isEmpty)
        assertTrue(EditPayload(frames = mapOf(3 to EditDocument())).isEmpty)
        assertFalse(EditPayload(ops = listOf(op("transform"))).isEmpty)
        assertFalse(EditPayload(frames = mapOf(3 to document("crop"))).isEmpty)
    }

    /** Backwards: the pre-ME-126 form *is* a valid new one, and it means every frame. */
    @Test
    fun `a recipe written before this ticket reads as all frames`() {
        val old = editorJson.encodeToString(document("transform"))

        val payload = editorJson.decodeFromString<EditPayload>(old)

        assertEquals(document("transform"), payload.documentFor(0))
        assertEquals(document("transform"), payload.documentFor(4))
        assertEquals(EditScope.AllFrames, payload.scopeOf(0))
    }

    /** Forwards: an older build draws the picture unedited rather than refusing it. */
    @Test
    fun `an older build decoding a keyed payload sees an empty document`() {
        val written = editorJson.encodeToString(EditPayload(frames = mapOf(3 to document("crop"))))

        val seen = editorJson.decodeFromString<EditDocument>(written)

        assertTrue(seen.isEmpty, "an unknown key reached a build that cannot honour it")
    }
}
