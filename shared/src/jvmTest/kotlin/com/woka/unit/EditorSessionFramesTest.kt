package com.woka.unit

import com.woka.core.data.MediaItem
import com.woka.feature.editor.core.preview
import com.woka.feature.editor.core.commit
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.core.layers
import com.woka.feature.editor.core.moveToFrame
import com.woka.feature.editor.core.rebase
import com.woka.support.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Walking between frames keeps each one's work (ME-169).
 *
 * The bug this closes was reported by hand: moving to another frame asked whether to
 * throw the current one away, because the host rebuilt the session and there was
 * genuinely nothing else it could do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorSessionFramesTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val media = MediaItem.Image(File("burst.heic"))

    private fun op(id: String) = ToolOp(id, ParamSet())

    private fun session(stored: Map<Int, EditDocument> = emptyMap()) = EditorSession(
        media = media,
        initial = stored[0] ?: EditDocument(),
        openedOn = 0,
        storedFor = { index -> stored[index] ?: EditDocument() }
    )

    private fun EditorSession.edit(id: String) {
        preview(op(id))
        commit(ToolCardinality.Single)
    }

    /** The thing the dialog used to ask about, and no longer has to. */
    @Test
    fun `work survives a walk to another frame and back`() {
        val session = session()
        session.edit("crop")

        session.moveToFrame(2, EditScope.SingleFrame)
        assertTrue(session.document.value.isEmpty, "frame 2 arrived carrying frame 0's work")

        session.moveToFrame(0, EditScope.SingleFrame)
        assertEquals(listOf(op("crop")), session.document.value.ops, "frame 0's work was lost")
    }

    /**
     * **Undo does not walk out of the frame you are looking at.** A step back on a
     * fresh frame would otherwise edit a picture off screen, which is the rule the
     * ticket set before a line was written.
     */
    @Test
    fun `undo belongs to the frame it was made on`() {
        val session = session()
        session.edit("crop")
        assertTrue(session.canUndo.value)

        session.moveToFrame(2, EditScope.SingleFrame)
        assertFalse(session.canUndo.value, "Undo offered to walk back through another frame")

        session.moveToFrame(0, EditScope.SingleFrame)
        assertTrue(session.canUndo.value, "the frame's own history did not come back")
    }

    /** A frame reached for the first time opens on what the file already says about it. */
    @Test
    fun `a first visit opens on the frame's stored edit`() {
        val session = session(stored = mapOf(2 to EditDocument(listOf(op("adjust")))))

        session.moveToFrame(2, EditScope.SingleFrame)

        assertEquals(listOf(op("adjust")), session.document.value.ops)
        assertFalse(session.isDirty.value, "a stored edit read as unsaved work")
    }

    /** Unsaved work two frames back is still unsaved when the window closes. */
    @Test
    fun `dirty follows every frame, not the open one`() {
        val session = session()
        session.edit("crop")

        session.moveToFrame(2, EditScope.SingleFrame)

        assertTrue(session.isDirty.value, "work on another frame stopped counting")
    }
}
