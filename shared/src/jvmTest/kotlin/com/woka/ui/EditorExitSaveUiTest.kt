package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.woka.app.shell.AppExitRequest
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.shell.saveAction
import com.woka.support.ExitScene
import com.woka.support.FixtureEngine
import com.woka.support.FixtureTool
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import com.woka.support.resetExitState
import com.woka.support.withEditorOpen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val NAME = "photo.jpg"
private const val PATH = "/pictures/photo.jpg"

/**
 * The two answers that decide what happens to the edit (ME-160).
 *
 * `Save` is the ordering the ticket exists for: the editor starts the write **before**
 * it answers, so `EditorSaveStatus.inFlight` is already 1 when the gate resumes and
 * ME-125's wait holds the window until the file has been written. Driven through
 * `FixtureEngine`'s `savingGate`, which is what makes the two halves visibly ordered
 * rather than merely both happening.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorExitSaveUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun reset() = resetExitState()

    private fun save(engine: FixtureEngine, session: EditorSession): () -> Unit =
        saveAction(engine, session, NAME, EditScope.AllFrames, {}, {})

    @Test
    fun `Save writes the edit, and the window goes only once the write has landed`() {
        val gate = CompletableDeferred<Unit>()
        val engine = FixtureEngine(result = EditSaveResult.Saved(PATH), savingGate = gate)
        var exits = 0
        onEditor(listOf(FixtureTool())) { harness ->
            val onSave = save(engine, harness.session)
            withEditorOpen {
                onUi {
                    setContent { ExitScene(isDirty = true, onSave = onSave, onExit = { exits++ }) }
                    AppExitRequest.request()
                    waitForIdle()

                    onNodeWithText("Save").performClick()
                    waitForIdle()
                    assertEquals(1, engine.saveRequests.size, "Save wrote nothing")
                    assertEquals(0, exits, "the window went while the write was running")

                    gate.complete(Unit)
                    waitForIdle()
                    assertEquals(1, exits, "the write landed and the window never closed")
                }
            }
        }
    }

    /** The user chose the loss, so nothing is written and nothing is waited for. */
    @Test
    fun `Don't save quits at once and writes nothing`() {
        val engine = FixtureEngine(result = EditSaveResult.Saved(PATH))
        var exits = 0
        onEditor(listOf(FixtureTool())) { harness ->
            val onSave = save(engine, harness.session)
            withEditorOpen {
                onUi {
                    setContent { ExitScene(isDirty = true, onSave = onSave, onExit = { exits++ }) }
                    AppExitRequest.request()
                    waitForIdle()

                    onNodeWithText("Don't save").performClick()
                    waitForIdle()
                    assertEquals(1, exits, "discarding did not let the window go")
                    assertTrue(engine.saveRequests.isEmpty(), "discarding wrote the file anyway")
                }
            }
        }
    }
}
