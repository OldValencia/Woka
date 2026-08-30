package com.woka.unit

import com.woka.feature.editor.EditorSaveStatus
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The four states the pill can be in, and the one message that is not ours to write.
 *
 * The object is a singleton and its message outlives any one test, which is what the
 * reset below is for — without it these pass or fail by the order they run in.
 */
class EditorSaveStatusTest {

    @BeforeTest
    fun reset() = EditorSaveStatus.clear()

    @Test
    fun `a running save names the file it is writing`() {
        EditorSaveStatus.saving("photo.jpg")
        assertEquals("Saving photo.jpg…", EditorSaveStatus.message)
    }

    @Test
    fun `a finished save names it too`() {
        EditorSaveStatus.saved("photo.jpg")
        assertEquals("Saved photo.jpg", EditorSaveStatus.message)
    }

    /**
     * Read in the film's folder, not in the editor (ME-124), so it carries its cause.
     */
    @Test
    fun `a save made on the way back to the video says why it happened`() {
        EditorSaveStatus.savedLeaving("photo.jpg")
        assertEquals("Saved photo.jpg before returning to the video", EditorSaveStatus.message)
    }

    /**
     * Verbatim, and this is the assertion that keeps it so: the save pipeline already
     * writes sentences aimed at the user, and a prefix bolted on here would be a second
     * vocabulary for the same failure.
     */
    @Test
    fun `a failure is passed through exactly as the save wrote it`() {
        val reason = "The source changed on disk. Nothing was written."
        EditorSaveStatus.failed(reason)
        assertEquals(reason, EditorSaveStatus.message)
    }

    @Test
    fun `clearing after a save leaves nothing to draw`() {
        EditorSaveStatus.saved("photo.jpg")
        EditorSaveStatus.clear()
        assertNull(EditorSaveStatus.message)
    }

    /** One message, one save: a second overwrites the first and nothing worse (ME-128). */
    @Test
    fun `a second save replaces the first message`() {
        EditorSaveStatus.saving("first.jpg")
        EditorSaveStatus.saving("second.jpg")
        assertEquals("Saving second.jpg…", EditorSaveStatus.message)
    }
}
