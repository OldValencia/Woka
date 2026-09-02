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
import kotlin.test.assertTrue

/**
 * Which layer each half of a sitting's work ends up in (2026-09-02).
 *
 * The write used to be handed a composed document per frame and could not tell the file's
 * half from the frame's, so an adjustment made for every picture was written onto the two
 * frames that happened to be visited and nowhere else.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorSessionLayersTest {

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
     * In Single Frame, `Done` writes each frame's **own** work into its own slot, and
     * the shared layer stays shared (2026-09-02).
     */
    @Test
    fun `done writes every touched frame in single frame`() {
        val session = session()
        session.edit("crop")
        session.moveToFrame(2, EditScope.SingleFrame)
        session.edit("adjust")

        val layers = session.layers(EditScope.SingleFrame)

        assertEquals(setOf(0, 2), layers.frames.keys)
        assertEquals(listOf(op("crop")), layers.frames.getValue(0).ops)
        assertEquals(listOf(op("adjust")), layers.frames.getValue(2).ops)
    }

    /**
     * **And one shared layer in All Frames**, with no key for the open frame: the switch
     * says the edit is for every picture, so it belongs in the base and nowhere else.
     */
    @Test
    fun `done writes one shared layer in all frames`() {
        val session = session()
        session.edit("crop")
        session.moveToFrame(2, EditScope.AllFrames)
        session.edit("adjust")

        val layers = session.layers(EditScope.AllFrames)

        assertEquals(listOf(op("crop"), op("adjust")), layers.base.ops)
        assertTrue(layers.frames.isEmpty(), "an All Frames edit was written to one frame")
    }

    /**
     * **The write that used to put the shared half in every visited frame.** Work done
     * in All Frames, then a switch and two frames touched: the adjustment belongs to the
     * base, and only the marks belong to the frames.
     */
    @Test
    fun `shared work stays in the base when frames are touched afterwards`() {
        val session = session()
        session.edit("adjust")
        session.rebase()
        session.edit("mark0")
        session.moveToFrame(2, EditScope.SingleFrame)
        session.edit("mark2")

        val layers = session.layers(EditScope.SingleFrame)

        assertEquals(listOf(op("adjust")), layers.base.ops, "the shared edit left the base")
        assertEquals(listOf(op("mark0")), layers.frames.getValue(0).ops)
        assertEquals(listOf(op("mark2")), layers.frames.getValue(2).ops)
    }
}
