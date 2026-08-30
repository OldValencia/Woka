package com.woka.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.woka.app.shell.TopBarAccessoriesState
import com.woka.core.ui.components.STATUS_SETTLE_MS
import com.woka.feature.editor.EditorSaveBanner
import com.woka.feature.editor.EditorSaveStatus
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.shell.saveAction
import com.woka.support.EditorHarness
import com.woka.support.EditorUnderShell
import com.woka.support.FixtureEngine
import com.woka.support.FixtureTool
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val NAME = "photo.jpg"
private const val PATH = "/pictures/photo.jpg"
private const val REFUSED = "The source changed on disk. Nothing was written."

/**
 * `Done` closes first and reports afterwards (ME-130).
 *
 * The editor is gone from the tree while the write is still suspended, which is the
 * whole behaviour: before this, the panel sat on screen and unresponsive for the length
 * of a native decode and encode, and a refusal had nowhere left to be shown.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorSaveBannerUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun reset() = EditorSaveStatus.clear()

    /** The editor, the banner above it, and a `Done` that unmounts the first. */
    @Composable
    private fun Scene(
        harness: EditorHarness,
        engine: EditorEngine,
        onSaved: (String) -> Unit = {}
    ) {
        var open by remember { mutableStateOf(true) }
        Box {
            if (open) {
                EditorUnderShell(
                    harness = harness,
                    accessories = TopBarAccessoriesState(),
                    onDone = saveAction(engine, harness.session, NAME, { open = false }, onSaved)
                )
            }
            EditorSaveBanner()
        }
    }

    @Test
    fun `the editor is gone while the write is still running, and the pill has it`() {
        val gate = CompletableDeferred<Unit>()
        val engine = FixtureEngine(result = EditSaveResult.Saved(PATH), savingGate = gate)
        var saved: String? = null
        onEditor(listOf(FixtureTool())) { harness ->
            onUi {
                setContent { Scene(harness, engine, onSaved = { saved = it }) }
                onNodeWithTag("editor-done").performClick()
                waitForIdle()

                // Without `EditorSaveStatus.scope` this is where the save dies: the
                // composable that launched it has just left the tree.
                onNodeWithTag("editor-screen").assertDoesNotExist()
                onNodeWithText("Saving $NAME…").assertIsDisplayed()
                assertNull(saved, "the folder was told before the write had answered")

                gate.complete(Unit)
                waitForIdle()
                onNodeWithText("Saved $NAME").assertIsDisplayed()
                assertEquals(PATH, saved, "the saved path never reached the folder")

                mainClock.advanceTimeBy(STATUS_SETTLE_MS + 600)
                waitForIdle()
                onNodeWithText("Saved $NAME").assertDoesNotExist()
            }
        }
    }

    @Test
    fun `a refused write still closes the editor, and says what happened in its words`() {
        val engine = FixtureEngine(result = EditSaveResult.Failed(REFUSED))
        onEditor(listOf(FixtureTool())) { harness ->
            onUi {
                setContent { Scene(harness, engine) }
                onNodeWithTag("editor-done").performClick()
                waitForIdle()

                onNodeWithTag("editor-screen").assertDoesNotExist()
                onNodeWithText(REFUSED).assertIsDisplayed()
            }
        }
    }

    /** Nothing was written and nothing was asked for: not an event, so not a pill. */
    @Test
    fun `an empty document closes the editor and shows nothing at all`() {
        onEditor(listOf(FixtureTool())) { harness ->
            onUi {
                setContent { Scene(harness, FixtureEngine()) }
                onNodeWithTag("editor-done").performClick()
                waitForIdle()

                onNodeWithTag("editor-screen").assertDoesNotExist()
                onNodeWithText("Saving $NAME…").assertDoesNotExist()
                assertNull(EditorSaveStatus.message, "an empty save left a message")
            }
        }
    }
}
