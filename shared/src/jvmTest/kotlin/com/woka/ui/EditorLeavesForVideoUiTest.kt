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
import com.woka.app.shell.TopBarAccessoriesState
import com.woka.feature.editor.EditorSaveBanner
import com.woka.feature.editor.EditorSaveStatus
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.shell.leaveForVideoAction
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
import kotlin.test.assertTrue

private const val NAME = "photo.jpg"
private const val LEFT = "Saved photo.jpg before returning to the video"

/**
 * The editor being asked to leave, because the video window is going back (ME-124).
 *
 * Driven through `leaveForVideoAction` rather than through `PipManager`, for the reason
 * `EditorSaveBannerUiTest` drives `saveAction`: this is the behaviour, and the route
 * change on the other side of it is `PipReturnTest`'s.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorLeavesForVideoUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @BeforeTest
    fun reset() = EditorSaveStatus.clear()

    private fun dirty(harness: EditorHarness) {
        val tool = FixtureTool()
        harness.apply(ToolOp(tool.manifest.id, tool.defaults()), ToolCardinality.Single)
    }

    /** The editor, the banner over it, and a `leave` the test can fire by hand. */
    @Composable
    private fun Scene(harness: EditorHarness, engine: FixtureEngine, leave: (() -> Unit) -> Unit) {
        var open by remember { mutableStateOf(true) }
        val action = leaveForVideoAction(engine, harness.session, NAME, { open = false }, {})
        leave(action)
        Box {
            if (open) EditorUnderShell(harness, TopBarAccessoriesState())
            EditorSaveBanner()
        }
    }

    @Test
    fun `a dirty editor is written and closed, and the pill says why`() {
        val gate = CompletableDeferred<Unit>()
        val engine = FixtureEngine(result = EditSaveResult.Saved("/p/$NAME"), savingGate = gate)
        onEditor(listOf(FixtureTool())) { harness ->
            dirty(harness)
            var leave: (() -> Unit)? = null
            onUi {
                setContent { Scene(harness, engine) { leave = it } }
                leave!!.invoke()
                waitForIdle()

                onNodeWithTag("editor-screen").assertDoesNotExist()
                onNodeWithText("Saving $NAME…").assertIsDisplayed()

                gate.complete(Unit)
                waitForIdle()
                onNodeWithText(LEFT).assertIsDisplayed()
            }
        }
    }

    @Test
    fun `a clean editor closes without writing anything`() {
        val engine = FixtureEngine(result = EditSaveResult.Saved("/p/$NAME"))
        onEditor(listOf(FixtureTool())) { harness ->
            var leave: (() -> Unit)? = null
            onUi {
                setContent { Scene(harness, engine) { leave = it } }
                leave!!.invoke()
                waitForIdle()

                onNodeWithTag("editor-screen").assertDoesNotExist()
                assertTrue(engine.saveRequests.isEmpty(), "an untouched file was rewritten")
                assertNull(EditorSaveStatus.message, "a pill claimed an edit that never happened")
            }
        }
    }

    /**
     * The race the design exists to prevent: the action is published once, invoked much
     * later, and must carry the document as it is **then**.
     */
    @Test
    fun `the write carries the document as it stands when the action runs`() {
        val engine = FixtureEngine(result = EditSaveResult.Saved("/p/$NAME"))
        val tool = FixtureTool()
        onEditor(listOf(tool)) { harness ->
            var leave: (() -> Unit)? = null
            onUi {
                setContent { Scene(harness, engine) { leave = it } }
                // Published against a clean session; edited afterwards.
                harness.apply(ToolOp(tool.manifest.id, tool.defaults()), ToolCardinality.Single)
                waitForIdle()

                leave!!.invoke()
                waitForIdle()

                assertEquals(1, engine.saveRequests.size, "the later edit was not saved")
                assertEquals(harness.session.document.value, engine.saveRequests.single())
            }
        }
    }
}
