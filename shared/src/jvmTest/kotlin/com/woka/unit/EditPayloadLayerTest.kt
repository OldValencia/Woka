package com.woka.unit

import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditPayload
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A frame's edit lies on top of the file's, and a file written before that rule still
 * means what it meant (ME-175).
 *
 * The compatibility half is the reason this file exists: the flag is invisible, and the
 * only thing that proves it works is a payload built without it drawing the old way.
 */
class EditPayloadLayerTest {

    private val warm = ToolOp("adjust", ParamSet())
    private val crop = ToolOp("crop", ParamSet())

    /** The rule every file on disk before 2026-09-02 was written under. */
    @Test
    fun `a legacy payload lets the frame's edit win outright`() {
        val legacy = EditPayload(ops = listOf(warm), frames = mapOf(2 to EditDocument(listOf(crop))))

        assertEquals(listOf(crop), legacy.documentFor(2).ops, "a legacy key stopped replacing")
        assertEquals(listOf(warm), legacy.documentFor(0).ops)
    }

    /** And the rule everything written since means. */
    @Test
    fun `a layered payload draws the base and then the frame's`() {
        val layered = EditPayload(
            ops = listOf(warm),
            frames = mapOf(2 to EditDocument(listOf(crop))),
            layered = true
        )

        assertEquals(listOf(warm, crop), layered.documentFor(2).ops, "the base was not carried")
        assertEquals(listOf(warm), layered.documentFor(0).ops)
    }

    /** An absent key adds nothing, which is the same document under either rule. */
    @Test
    fun `a frame with no key of its own draws the base either way`() {
        val ops = listOf(warm)
        assertEquals(ops, EditPayload(ops = ops).documentFor(5).ops)
        assertEquals(ops, EditPayload(ops = ops, layered = true).documentFor(5).ops)
    }

    /**
     * **The write that used to lose work.** Frame 2 for itself, then frame 0 for every
     * frame: before ME-175 the second `with` returned a payload with no `frames` at all.
     */
    @Test
    fun `writing all frames no longer discards the keys`() {
        val after = EditPayload()
            .with(EditDocument(listOf(crop)), EditScope.SingleFrame, 2)
            .with(EditDocument(listOf(warm)), EditScope.AllFrames, 0)

        assertTrue(after.frames.containsKey(2), "frame 2's work was discarded")
        assertEquals(listOf(warm), after.ops)
        assertEquals(listOf(warm, crop), after.documentFor(2).ops)
    }

    /**
     * The delta, not the document. The editor opens on the composed document, so storing
     * it whole would apply the base twice on the one frame somebody worked on.
     */
    @Test
    fun `a per-frame write stores only what the base does not already carry`() {
        val base = EditPayload(ops = listOf(warm), layered = true)
        val composed = EditDocument(listOf(warm, crop))

        val after = base.with(composed, EditScope.SingleFrame, 3)

        assertEquals(listOf(crop), after.frames.getValue(3).ops, "the base was stored twice")
        assertEquals(listOf(warm, crop), after.documentFor(3).ops)
    }

    /** Anything this build writes means this build's rule. */
    @Test
    fun `writing marks the payload layered`() {
        assertTrue(EditPayload().with(EditDocument(listOf(warm)), EditScope.AllFrames, 0).layered)
        assertTrue(EditPayload().with(EditDocument(listOf(crop)), EditScope.SingleFrame, 1).layered)
    }

    /**
     * **Emptying the base no longer folds a key to nothing** (ME-175).
     *
     * Clearing every slider in All Frames used to take the whole recipe with it,
     * overrides included, because the write discarded them. "All frames: nothing" is a
     * statement about the base and says nothing about a frame carrying its own work —
     * throwing that away is Revert's, and **ME-176** gives Revert the scope to say which.
     */
    @Test
    fun `emptying the base leaves a frame's own edit alone`() {
        val after = EditPayload()
            .with(EditDocument(listOf(crop)), EditScope.SingleFrame, 3)
            .with(EditDocument(), EditScope.AllFrames, 0)

        assertTrue(after.ops.isEmpty())
        assertEquals(listOf(crop), after.frames.getValue(3).ops, "the key went with the base")
        assertTrue(!after.isEmpty, "a payload still drawing something read as empty")
    }
}
