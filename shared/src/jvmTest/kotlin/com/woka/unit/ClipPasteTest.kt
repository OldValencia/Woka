package com.woka.unit

import com.woka.core.data.MediaItem
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.NO_CLIP
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.core.layers
import com.woka.feature.editor.core.ownOpsOf
import com.woka.feature.editor.core.pasteOnClip
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
 * Pasting one clip's look onto another (ME-267): the kinds picked replace the target's own,
 * the rest of the target is kept, and the paste is one step `Ctrl+Z` takes back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClipPasteTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun op(id: String) = ToolOp(id, ParamSet())

    private val shared = EditDocument(listOf(op("trim")))
    private val isSound = { it: ToolOp -> it.toolId == "sound" }

    /** Clip 0 graded and muted; clip 1 with a filter and its own sound. */
    private fun film() = EditorSession(
        media = MediaItem.Video(File("film.mp4")),
        initial = shared,
        openedOn = NO_CLIP,
        storedFor = { key ->
            when (key) {
                0 -> EditDocument(shared.ops + op("adjust") + op("sound"))
                1 -> EditDocument(shared.ops + op("filter") + op("sound"))
                else -> shared
            }
        },
        storedBase = shared
    )

    @Test
    fun `a look replaces the look and keeps the sound`() {
        val session = film()
        session.pasteOnClip(1, session.ownOpsOf(0)) { !isSound(it) }
        assertEquals(listOf(op("sound"), op("adjust")), session.ownOpsOf(1))
        assertEquals(shared, session.document.value, "the document on screen moved")
    }

    @Test
    fun `undo puts the clip back, redo pastes it again`() {
        val session = film()
        session.pasteOnClip(2, session.ownOpsOf(0)) { true }
        assertEquals(listOf(op("adjust"), op("sound")), session.ownOpsOf(2))

        session.undo()
        assertEquals(shared, session.document.value, "undo went past the paste")
        assertEquals(emptyList(), session.ownOpsOf(2))
        assertFalse(session.layers(EditScope.AllFrames).frames.containsKey(2), "Done would keep an undone paste")

        session.redo()
        assertEquals(listOf(op("adjust"), op("sound")), session.ownOpsOf(2))
    }

    @Test
    fun `pasting what the clip already has is no step`() {
        val session = film()
        session.pasteOnClip(0, session.ownOpsOf(0)) { true }
        // The recipe's own steps are all that is there: one undo leaves the cut.
        session.undo()
        assertEquals(EditDocument(), session.document.value)
        assertTrue(session.canRedo.value)
    }
}
