package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import com.woka.app.shell.EXIT_GRACE_MS
import com.woka.app.shell.ExitWhenQuiet
import com.woka.feature.editor.EditorSaveStatus
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.shell.saveAction
import com.woka.support.FixtureEngine
import com.woka.support.FixtureTool
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import com.woka.support.resetExitState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val NAME = "photo.jpg"
private const val PATH = "/pictures/photo.jpg"

/**
 * The exit gate (ME-125): `Done` and then the close button writes the edit.
 *
 * The save is driven through `saveAction` rather than by calling `started()` here,
 * because the bracket around that launch is half of what the ticket adds — a test
 * that counted for itself would pass with the bracket deleted.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class AppExitUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /**
     * Three singletons carry state between tests. `EditorPresence` matters as much as
     * the count here: an editor left open by another class sends every case below down
     * ME-160's branch, where they would wait for a verdict nobody gives.
     */
    @BeforeTest
    fun reset() = resetExitState()

    /** Nothing is running, so the gate is not a gate — the common case, every time. */
    @Test
    fun `a window with no save behind it closes on the first frame`() {
        var exits = 0
        onUi {
            setContent { ExitWhenQuiet(requested = true) { exits++ } }
            waitForIdle()
            assertEquals(1, exits, "an idle window did not close")
        }
    }

    /** Not requested is not deferred: the effect must do nothing at all until asked. */
    @Test
    fun `a window nobody asked to close stays open`() {
        var exits = 0
        onUi {
            setContent { ExitWhenQuiet(requested = false) { exits++ } }
            waitForIdle()
            assertEquals(0, exits, "the application closed itself")
        }
    }

    @Test
    fun `a running save holds the window open, and releasing it closes exactly once`() {
        val gate = CompletableDeferred<Unit>()
        val engine = FixtureEngine(result = EditSaveResult.Saved(PATH), savingGate = gate)
        var exits = 0
        onEditor(listOf(FixtureTool())) { harness ->
            saveAction(engine, harness.session, NAME, EditScope.AllFrames, {}, {})()
            onUi {
                setContent { ExitWhenQuiet(requested = true) { exits++ } }
                waitForIdle()

                // This is the loss the ticket is about: the editor is already gone
                // and the write is still between the temp file and the atomic move.
                assertEquals(0, exits, "the window closed over a write still running")

                gate.complete(Unit)
                waitForIdle()
                assertEquals(1, exits, "the write finished and the window did not close")
            }
        }
    }

    /**
     * A save gets no veto. The original is intact whatever happens to the recipe, and
     * an application that will not quit is the worse defect (ME-125).
     */
    @Test
    fun `a save that never answers still lets the application go`() {
        val engine =
            FixtureEngine(
                result = EditSaveResult.Saved(PATH),
                savingGate = CompletableDeferred()
            )
        var exits = 0
        onEditor(listOf(FixtureTool())) { harness ->
            saveAction(engine, harness.session, NAME, EditScope.AllFrames, {}, {})()
            onUi {
                setContent { ExitWhenQuiet(requested = true) { exits++ } }
                waitForIdle()
                assertEquals(0, exits, "the grace period was not waited out")

                // Virtual time, never a real five-second test.
                mainClock.advanceTimeBy(EXIT_GRACE_MS + 100)
                waitForIdle()
                assertEquals(1, exits, "a hung save kept the application open")
            }
        }
    }
}
