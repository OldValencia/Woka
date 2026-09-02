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
import com.woka.feature.editor.core.detachFrame
import com.woka.feature.editor.core.layers
import com.woka.feature.editor.core.moveToFrame
import com.woka.feature.editor.core.ownOpsOfOpenFrame
import com.woka.feature.editor.core.rebase
import com.woka.support.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What pressing the scope switch does to what is on screen (reported by hand
 * 2026-09-02).
 *
 * It used to do nothing at all, which is how work made for one frame ended up riding to
 * every other one: the switch left the frame's composition showing, and the next walk
 * declared that composition the shared layer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorSessionSwitchTest {

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
     * **The walk reported on 2026-09-02.** Work made in `Single Frame`, then the switch
     * moved to `All Frames` and another frame reached — and Styles, filters and markup
     * all rode along, because the switch left the frame's own composition on screen and
     * the next walk declared it the shared layer.
     */
    @Test
    fun `single-frame work does not ride to other frames after switching to all frames`() {
        val session = session()
        session.rebase()
        session.edit("markup")

        session.detachFrame()
        session.moveToFrame(2, EditScope.AllFrames)

        assertTrue(session.document.value.isEmpty, "frame 0's own work rode to frame 2")
    }

    /** And it is not lost — it is waiting on the frame it was made on. */
    @Test
    fun `the frame keeps its own work while all frames is shown`() {
        val session = session()
        session.rebase()
        session.edit("markup")

        session.detachFrame()
        session.moveToFrame(2, EditScope.AllFrames)
        session.moveToFrame(0, EditScope.SingleFrame)

        assertEquals(listOf(op("markup")), session.document.value.ops, "the frame's work was lost")
    }

    /**
     * **A shared edit made while the frame was away reaches it.** The stash holds a
     * composed picture, so without recomposing on arrival the frame would come back
     * showing the layer as it was when it left.
     */
    @Test
    fun `a stashed frame picks up shared work made while it was away`() {
        val session = session()
        session.rebase()
        session.edit("markup")
        session.detachFrame()

        session.moveToFrame(2, EditScope.AllFrames)
        session.edit("adjust")
        session.rebase()
        session.moveToFrame(0, EditScope.SingleFrame)

        assertEquals(
            listOf(op("adjust"), op("markup")),
            session.document.value.ops,
            "the frame came back without the shared edit"
        )
    }

    /** Revert must not offer to remove a layer this frame does not have. */
    @Test
    fun `a frame with nothing of its own has no own ops`() {
        val session = session()
        session.edit("adjust")
        session.rebase()

        assertTrue(session.ownOpsOfOpenFrame().isEmpty(), "shared work read as the frame's")
    }

    /**
     * **Reported verbatim on 2026-09-02.** All Frames, a mark, check the other frames;
     * on another frame switch to Single Frame and add a second mark; switch that frame
     * back to All Frames — the second mark went; switch to Single Frame again — still
     * gone.
     *
     * `detachFrame` put the frame's own work in the stash and `rebase` never took it
     * back, so it sat there for a frame the editor was sitting on: invisible, and
     * dropped by [layers] when `Done` came.
     */
    @Test
    fun `a frame's own mark survives a switch to all frames and back`() {
        val session = session()
        session.edit("markA")

        session.moveToFrame(2, EditScope.AllFrames)
        session.rebase()
        session.edit("markB")

        session.detachFrame()
        assertEquals(listOf(op("markA")), session.document.value.ops, "All Frames showed the frame's own")

        session.rebase()
        assertEquals(
            listOf(op("markA"), op("markB")),
            session.document.value.ops,
            "the frame's own mark did not come back"
        )
    }

    /** And `Done` writes it, which is the half that would have lost the file's copy. */
    @Test
    fun `the mark stranded by the switch is still written`() {
        val session = session()
        session.edit("markA")
        session.moveToFrame(2, EditScope.AllFrames)
        session.rebase()
        session.edit("markB")

        session.detachFrame()
        session.rebase()

        val layers = session.layers(EditScope.SingleFrame)
        assertEquals(listOf(op("markA")), layers.base.ops)
        assertEquals(listOf(op("markB")), layers.frames.getValue(2).ops, "frame 2's mark was dropped")
    }
}
