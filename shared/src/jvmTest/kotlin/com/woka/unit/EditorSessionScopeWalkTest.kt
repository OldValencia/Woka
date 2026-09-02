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
import com.woka.feature.editor.core.detachFrame
import com.woka.feature.editor.core.ownOpsOfOpenFrame
import com.woka.feature.editor.core.rebase
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
 * What a frame change means depends on the switch (reported by hand 2026-09-02).
 *
 * `All Frames` and `Single Frame` are two different answers to "whose edit is this",
 * and walking between frames has to give each of them its own — which is the thing
 * ME-169 got wrong by making the per-frame stash unconditional.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorSessionScopeWalkTest {

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
     * **The bug this pair of modes exists to avoid** (reported by hand 2026-09-02).
     *
     * `All Frames` says the edit is for every picture in the file, so walking to another
     * one must go on showing it. It did not: the document was put aside per frame
     * whatever the switch said, and a warmed burst looked warmed on frame 0 and untouched
     * on frame 2 until `Done` was pressed.
     */
    @Test
    fun `an all-frames edit travels to every frame`() {
        val session = session()
        session.edit("adjust")

        session.moveToFrame(2, EditScope.AllFrames)

        assertEquals(listOf(op("adjust")), session.document.value.ops, "the edit stayed behind")
        assertTrue(session.canUndo.value, "the history stayed behind")
    }

    /** And the two modes do not interfere: Single Frame still keeps them apart. */
    @Test
    fun `single frame still keeps each frame's work to itself`() {
        val session = session()
        session.edit("adjust")

        session.moveToFrame(2, EditScope.SingleFrame)

        assertTrue(session.document.value.isEmpty, "frame 2 inherited frame 0's work")
    }

    /**
     * **The walk that lost everything** (reported by hand 2026-09-02).
     *
     * Adjust and Markup in `All Frames`, over to another frame, switch to `Single Frame`,
     * add a mark, walk back — and the original frame was blank. The shared work had been
     * stashed as the *second* frame's private property the moment the switch moved, and
     * the first frame, never visited, opened on what the file said: nothing.
     */
    @Test
    fun `all-frames work survives a switch to single frame and a walk back`() {
        val session = session()
        session.edit("adjust")
        session.edit("markup")

        session.moveToFrame(2, EditScope.AllFrames)
        session.rebase()
        session.edit("markup2")

        session.moveToFrame(0, EditScope.SingleFrame)

        assertEquals(
            listOf(op("adjust"), op("markup")),
            session.document.value.ops,
            "the All Frames work did not reach the frame it was made on"
        )
    }

    /** And the frame it was added on keeps the extra, which the other must not have. */
    @Test
    fun `the frame the extra was added on keeps it`() {
        val session = session()
        session.edit("adjust")
        session.moveToFrame(2, EditScope.AllFrames)
        session.rebase()
        session.edit("markup2")

        session.moveToFrame(0, EditScope.SingleFrame)
        session.moveToFrame(2, EditScope.SingleFrame)

        assertTrue(op("markup2") in session.document.value.ops, "frame 2 lost its own mark")
    }
}
