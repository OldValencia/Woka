package com.woka.unit

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
}
