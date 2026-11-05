package com.woka.unit

import com.woka.core.data.MediaItem
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.NO_CLIP
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.core.commit
import com.woka.feature.editor.core.focusClip
import com.woka.feature.editor.core.layers
import com.woka.feature.editor.core.ownOpsOf
import com.woka.feature.editor.core.pickClip
import com.woka.feature.editor.core.preview
import com.woka.feature.editor.core.useLinearHistory
import com.woka.support.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * A film's selected clip is its scope, and selecting is a step (ME-221): one line of history
 * walks back through edits and selections alike, and a cut made with a clip selected is still
 * the whole film's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClipFocusTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun op(id: String) = ToolOp(id, ParamSet())

    private val shared = EditDocument(listOf(op("trim")))

    private fun film() = EditorSession(
        media = MediaItem.Video(File("film.mp4")),
        initial = shared,
        openedOn = NO_CLIP,
        storedFor = { shared },
        storedBase = shared
    ).apply { useLinearHistory(setOf("trim", "crop")) }

    private fun EditorSession.edit(id: String) {
        preview(op(id))
        commit(ToolCardinality.Single)
    }

    @Test
    fun `undo walks back the edit, then the selection`() {
        val session = film()
        session.pickClip("b", 1)
        session.edit("adjust")
        assertEquals(listOf(op("adjust")), session.ownOpsOf(1))

        session.undo()
        assertEquals("b", session.clipSelection.value)
        assertEquals(emptyList(), session.ownOpsOf(1))
        session.undo()
        assertNull(session.clipSelection.value)
        assertEquals(NO_CLIP, session.frame)
        assertEquals(shared, session.document.value)
    }

    @Test
    fun `leaving a clip keeps the shared layer's history`() {
        val session = film()
        session.edit("filter")
        session.pickClip("b", 1)
        session.pickClip(null, NO_CLIP)
        repeat(3) { session.undo() }
        assertEquals(shared, session.document.value, "the edit made before the clip was opened is not undoable")
    }

    @Test
    fun `a cut made with a clip selected is the whole film's`() {
        val session = film()
        session.pickClip("b", 1)
        session.edit("adjust")
        session.edit("crop")
        assertEquals(listOf(op("adjust")), session.ownOpsOf(1))
        val written = session.layers(EditScope.SingleFrame)
        assertEquals(listOf(op("trim"), op("crop")), written.base.ops)
        assertEquals(listOf(op("adjust")), written.frames.getValue(1).ops)
    }

    @Test
    fun `a delete that clears the selection is undone with it`() {
        val session = film()
        session.pickClip("b", 1)
        session.focusClip(null, NO_CLIP)
        session.edit("trim")
        assertNull(session.clipSelection.value)
        session.undo()
        assertEquals("b", session.clipSelection.value)
        assertEquals(1, session.frame)
    }

    @Test
    fun `selecting a clip with nothing changed is not unsaved work`() {
        val session = film()
        session.pickClip("b", 1)
        assertFalse(session.isDirty.value)
    }
}
