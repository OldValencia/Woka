package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.woka.app.shell.CanvasOverrideState
import com.woka.app.shell.TopBarAccessoriesState
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.shell.EditorScreen
import com.woka.support.EditorUnderShell
import com.woka.support.FixtureEngine
import com.woka.support.FixtureParams
import com.woka.support.FixtureTool
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What the editor shows on a file that already carries an edit (ME-134).
 *
 * The panel is rendered from the document, so a session seeded with the file's own
 * operations is what puts the controls where the user left them. Before this, the
 * inspector drew defaults over a picture that was already turned.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorReopenUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun carriedBy(tool: FixtureTool) = EditDocument(
        ops = listOf(ToolOp("adjust", tool.defaults().with(FixtureParams.Count, 7)))
    )

    /**
     * What the by-hand run of 2026-08-19 asked for: a photograph that carries an edit
     * says so the moment it opens, and its own operations are behind Undo.
     */
    @Test
    fun `a reopened file reads as edited and has something to undo`() {
        val tool = FixtureTool(id = "adjust", domain = ToolDomain.Color)
        onEditor(listOf(tool), carriedBy(tool)) { harness ->
            onUi {
                val override = CanvasOverrideState()
                setContent { EditorUnderShell(harness, TopBarAccessoriesState(), override) }

                assertTrue(override.edited, "an edited photograph opened as an original")
                assertTrue(harness.session.canUndo.value, "its own edit is not undoable")
            }
        }
    }

    @Test
    fun `a reopened file shows the panel with its values rather than its defaults`() {
        val tool = FixtureTool(id = "adjust", domain = ToolDomain.Color)
        onEditor(listOf(tool), carriedBy(tool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(harness.session, FixtureEngine(), harness.registry, {}, {})
                }
                onNodeWithTag("editor-tool-adjust").performClick()

                // The spec's default is 1; 7 can only have come from the document.
                onNodeWithText("7").assertIsDisplayed()
            }
        }
    }
}
