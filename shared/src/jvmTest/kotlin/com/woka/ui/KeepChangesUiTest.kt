package com.woka.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.woka.feature.editor.EditorSaveStatus
import com.woka.feature.editor.core.ToolOp
import com.woka.support.FixtureEngine
import com.woka.support.FixtureTool
import com.woka.support.MainDispatcherRule
import com.woka.support.WaysOutHost
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Keep your changes?` (RD-401, `#dialogs/keep`): asked only with unsaved edits, and each of
 * its three answers does what it says.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class KeepChangesUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @AfterTest
    fun reset() = EditorSaveStatus.clear()

    /** Opens the host over a session with one unsaved edit, and presses `Cancel`. */
    private fun asked(block: ComposeUiTest.(FixtureEngine, () -> Int) -> Unit) =
        onEditor(listOf(FixtureTool())) { harness ->
            val engine = FixtureEngine()
            var closed = 0
            onUi {
                setContent { WaysOutHost(harness, engine, onClose = { closed++ }) }
                harness.apply(ToolOp("fixture"))
                waitForIdle()
                onNodeWithTag("host-leave").performClick()
                waitForIdle()
                onNodeWithText("Keep your changes?").assertIsDisplayed()
                onNodeWithText("You can come back and edit again at any time.").assertIsDisplayed()
                block(engine) { closed }
            }
        }

    @Test
    fun `Keep editing stays, and nothing is saved`() = asked { engine, closed ->
        onNodeWithTag("editor-keep-editing").performClick()
        waitForIdle()
        onNodeWithText("Keep your changes?").assertDoesNotExist()
        assertEquals(0, closed())
        assertTrue(engine.saveRequests.isEmpty())
    }

    @Test
    fun `Discard changes closes without a save`() = asked { engine, closed ->
        onNodeWithTag("editor-keep-discard").performClick()
        waitForIdle()
        assertEquals(1, closed())
        assertTrue(engine.saveRequests.isEmpty())
    }

    @Test
    fun `Keep changes saves and closes`() = asked { engine, closed ->
        onNodeWithTag("editor-keep-keep").performClick()
        waitForIdle()
        assertEquals(1, closed())
        assertEquals(listOf("fixture"), engine.saveRequests.single().ops.map { it.toolId })
    }

    @Test
    fun `nothing unsaved leaves without asking`() = onEditor(listOf(FixtureTool())) { harness ->
        var closed = 0
        onUi {
            setContent { WaysOutHost(harness, FixtureEngine(), onClose = { closed++ }) }
            onNodeWithTag("host-leave").performClick()
            waitForIdle()
            onNodeWithText("Keep your changes?").assertDoesNotExist()
            assertEquals(1, closed)
        }
    }
}
