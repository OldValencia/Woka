package com.woka.unit

import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorFrames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The one rule that decides whether the frame band exists at all (ME-156).
 *
 * It is a single boolean expression, and it is worth its own test because two very
 * different files answer false for two different reasons — and getting either wrong
 * puts a control on screen that cannot mean anything.
 */
class EditorFramesTest {

    @Test
    fun `a burst offers per-frame editing`() {
        assertTrue(EditorFrames(count = 5).allowsPerFrame)
    }

    @Test
    fun `a photograph does not`() {
        assertFalse(EditorFrames(count = 1).allowsPerFrame)
    }

    /** An edit on one frame of eight is a flicker rather than an edit (ME-138, ME-140). */
    @Test
    fun `an animation does not, however many frames it has`() {
        assertFalse(EditorFrames(count = 8, playsAsAnimation = true).allowsPerFrame)
    }

    /** What every ordinary photograph is, and what a failed read falls back to. */
    @Test
    fun `the defaults describe a single unedited picture`() {
        val frames = EditorFrames()

        assertEquals(1, frames.count)
        assertEquals(EditScope.AllFrames, frames.storedScope)
        assertTrue(frames.overridden.isEmpty())
        assertFalse(frames.playsAsAnimation)
    }
}
