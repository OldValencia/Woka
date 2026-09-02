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
 * What a frame looks like the **first time** the editor reaches it (2026-09-02).
 *
 * Two things meet here and both were wrong: the frame's own stored work, which the
 * shared layer replaced instead of adding to, and what the frame is measured against,
 * which was set to that layer and made real work read as clean.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorSessionFirstVisitTest {

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
     * **A frame carrying its own stored work keeps it** on a first visit.
     *
     * The first cut of `firstSightOf` returned the shared layer alone whenever this
     * sitting had one, so arriving at a frame the file had a crop for showed the crop
     * gone — and `Done` then wrote the frame without it, destroying what the file held.
     */
    @Test
    fun `a stored frame keeps its own work when the sitting has a shared layer`() {
        val session = EditorSession(
            media = MediaItem.Image(File("burst.heic")),
            initial = EditDocument(listOf(op("warm"))),
            openedOn = 0,
            storedFor = { index ->
                if (index == 2) EditDocument(listOf(op("warm"), op("crop")))
                else EditDocument(listOf(op("warm")))
            },
            storedBase = EditDocument(listOf(op("warm")))
        )
        session.edit("adjust")
        session.rebase()

        session.moveToFrame(2, EditScope.SingleFrame)

        assertEquals(
            listOf(op("warm"), op("adjust"), op("crop")),
            session.document.value.ops,
            "frame 2's own crop was thrown away"
        )
    }

    /**
     * **And it reports itself dirty against the file, not against the layer.**
     *
     * `openedWith` was set to the unsaved shared layer, so a frame carrying real work
     * read clean — and `layers` filters a stashed frame on exactly that, so the work was
     * never written.
     */
    @Test
    fun `a frame edited after a first visit is written`() {
        val session = EditorSession(
            media = MediaItem.Image(File("burst.heic")),
            initial = EditDocument(),
            openedOn = 0,
            storedFor = { EditDocument() }
        )
        session.edit("adjust")
        session.rebase()

        session.moveToFrame(2, EditScope.SingleFrame)
        session.edit("mark")
        session.moveToFrame(0, EditScope.SingleFrame)

        val layers = session.layers(EditScope.SingleFrame)
        assertEquals(listOf(op("mark")), layers.frames.getValue(2).ops, "frame 2 was dropped")
    }

    /** A frame the sitting never opened keeps whatever the file says about it. */
    @Test
    fun `an unvisited frame is not in the touched set`() {
        val session = session()
        session.edit("adjust")

        val layers = session.layers(EditScope.SingleFrame)

        assertEquals(setOf(0), layers.touched)
        assertTrue(4 !in layers.touched)
    }
}
