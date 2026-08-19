package com.woka.unit

import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolOp
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditorSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val Brightness = FloatParam("brightness", "Brightness", -1f..1f, default = 0f)

private fun adjust(value: Float) = ToolOp("adjust", ParamSet().with(Brightness, value))

/** What a file already carries — the document a reopened session starts from. */
private val carried = EditDocument(ops = listOf(adjust(0.25f)))

/** Two operations, so undo has somewhere to stop between the file and the original. */
private val twoCarried = EditDocument(ops = listOf(adjust(0.25f), adjust(0.5f)))

/**
 * Undo granularity is the contract under test: a drag is one step, not one per
 * frame (§3.1).
 *
 * `UnconfinedTestDispatcher` rather than the rule's default, because the session's
 * derived flows are `stateIn(…, Eagerly)` — under a queued dispatcher their
 * `.value` would read as the initial until the test pumped it, which would be
 * testing the dispatcher rather than the session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorSessionTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `a fresh session is clean and can do neither`() = onEditorSession { session ->
        assertTrue(session.document.value.isEmpty)
        assertFalse(session.isDirty.value)
        assertFalse(session.canUndo.value)
        assertFalse(session.canRedo.value)
    }

    @Test
    fun `two hundred previews and one commit are one history entry`() = onEditorSession { session ->
        repeat(200) { step -> session.preview(adjust(step / 200f)) }
        assertFalse(session.canUndo.value, "preview must not push history")

        session.commit(ToolCardinality.Single)

        assertTrue(session.canUndo.value)
        assertEquals(1, session.document.value.ops.size)
        session.undo()
        assertFalse(session.canUndo.value)
        assertTrue(session.document.value.isEmpty)
    }

    @Test
    fun `isDirty is false again after undoing back to the start`() = onEditorSession { session ->
        session.preview(adjust(0.5f))
        session.commit(ToolCardinality.Single)
        assertTrue(session.isDirty.value)

        session.undo()

        assertFalse(session.isDirty.value)
        assertTrue(session.canRedo.value)
    }

    @Test
    fun `an aborted drag leaves no trace`() = onEditorSession { session ->
        session.preview(adjust(0.5f))
        session.cancelPending()

        assertTrue(session.document.value.isEmpty)
        assertFalse(session.canUndo.value)
    }

    @Test
    fun `reset is itself undoable`() = onEditorSession { session ->
        session.preview(adjust(0.5f))
        session.commit(ToolCardinality.Single)
        session.reset()

        assertTrue(session.document.value.isEmpty)
        session.undo()
        assertEquals(0.5f, session.document.value.ops.single().params[Brightness])
    }

    /**
     * Reopening an edited photograph is not a change to it (ME-134): `isDirty` means
     * "differs from what is in the file", so `Cancel` must not ask about changes
     * nobody made — **and yet Undo is live**, because the file's own recipe is the
     * history behind it.
     */
    @Test
    fun `a session opened on an edited file is clean but can undo`() =
        onEditorSession(carried) { session ->
            assertEquals(carried, session.document.value)
            assertFalse(session.isDirty.value)
            assertTrue(session.canUndo.value, "the file's own edit is not undoable")
            assertFalse(session.canRedo.value)
        }

    /** The dead-buttons symptom, asserted: undo walks back into earlier sittings. */
    @Test
    fun `undo steps back through the operations the file carries`() =
        onEditorSession(twoCarried) { session ->
            session.undo()
            assertEquals(1, session.document.value.ops.size)
            assertTrue(session.isDirty.value, "the document no longer matches the file")

            session.undo()
            assertTrue(session.document.value.isEmpty, "undo never reached the original")
            assertFalse(session.canUndo.value)

            session.redo()
            assertEquals(1, session.document.value.ops.size)
        }

    /** `reset` is "back to where this sitting started", never Revert (ME-107). */
    @Test
    fun `reset returns to the file's document rather than to empty`() =
        onEditorSession(carried) { session ->
            session.preview(adjust(0.75f))
            session.commit(ToolCardinality.Single)

            session.reset()

            assertEquals(carried, session.document.value)
            assertFalse(session.isDirty.value)
        }
}
