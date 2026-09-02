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
import com.woka.feature.editor.core.moveToFrame
import com.woka.feature.editor.core.revertedToOriginal
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
 * What Revert leaves in **memory**, which is a different question from what it leaves in
 * the file (reported by hand 2026-09-02).
 *
 * `revertedToOriginal` was written when the session held one frame, and ME-169 gave it a
 * stash and a shared layer without it being told. The file was emptied and the work came
 * back on the next walk.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorSessionRevertTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun op(id: String) = ToolOp(id, ParamSet())

    private fun session() = EditorSession(
        media = MediaItem.Image(File("burst.heic")),
        initial = EditDocument(),
        openedOn = 0,
        storedFor = { EditDocument() }
    )

    private fun EditorSession.edit(id: String) {
        preview(op(id))
        commit(ToolCardinality.Single)
    }

    /**
     * **Revert must reach the frames nobody is looking at** (reported by hand
     * 2026-09-02).
     *
     * An All Frames revert empties the file, and it cleared only the open frame's
     * document — so the work came back the moment the editor walked to a stashed frame,
     * from a file that no longer carried any of it.
     */
    @Test
    fun `an all-frames revert forgets every frame`() {
        val session = session()
        session.edit("adjust")
        session.moveToFrame(2, EditScope.SingleFrame)
        session.edit("markup")

        session.revertedToOriginal(EditScope.AllFrames)
        session.moveToFrame(0, EditScope.SingleFrame)

        assertTrue(session.document.value.isEmpty, "frame 0's work came back from nowhere")
        assertFalse(session.isDirty.value, "the session still claims unsaved work")
    }

    /** A single-frame revert takes one layer off and leaves the rest of the sitting. */
    @Test
    fun `a single-frame revert leaves the other frames alone`() {
        val session = session()
        session.edit("adjust")
        session.moveToFrame(2, EditScope.SingleFrame)
        session.edit("markup")

        session.revertedToOriginal(EditScope.SingleFrame)
        session.moveToFrame(0, EditScope.SingleFrame)

        assertEquals(listOf(op("adjust")), session.document.value.ops, "frame 0 lost its work")
    }
}
