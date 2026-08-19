package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.ToolCardinality
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolOp
import com.woka.feature.editor.shell.EditorScreen
import com.woka.support.FixtureEngine
import com.woka.support.FixtureTool
import com.woka.support.MainDispatcherRule
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test

/**
 * The shell, driven end to end against `FixtureTool` and `FixtureEngine` — no real
 * tool, no real medium, which is what ME-011 built them for.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorShellUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun toolsInEveryDomain() =
        ToolDomain.entries.map { FixtureTool(id = it.name.lowercase(), domain = it) }

    @Test
    fun `the rail lists one tool per domain with a divider at each boundary`() {
        val tools = toolsInEveryDomain()
        onEditor(tools) { harness ->
            onUi {
                setContent {
                    EditorScreen(
                        session = harness.session,
                        engine = FixtureEngine(),
                        registry = harness.registry,
                        onCancel = {},
                        onDone = {}
                    )
                }
                ToolDomain.entries.forEach {
                    onNodeWithTag("editor-tool-${it.name.lowercase()}").assertIsDisplayed()
                }
                // Four domains, three boundaries — never a divider per tool.
                onAllNodesWithTag("editor-rail-divider").assertCountEquals(3)
            }
        }
    }

    @Test
    fun `clicking a tool opens its inspector and clicking it again closes it`() {
        val tool = FixtureTool(id = "adjust", domain = ToolDomain.Color)
        onEditor(listOf(tool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(harness.session, FixtureEngine(), harness.registry, {}, {})
                }
                onNodeWithTag("editor-inspector").assertDoesNotExist()

                onNodeWithTag("editor-tool-adjust").performClick()
                onNodeWithTag("editor-inspector-title").assertIsDisplayed()

                onNodeWithTag("editor-tool-adjust").performClick()
                onNodeWithTag("editor-inspector").assertDoesNotExist()
            }
        }
    }

    @Test
    fun `disabling the active tool closes its inspector with nothing clicked`() {
        val tool = FixtureTool(id = "adjust", domain = ToolDomain.Color)
        onEditor(listOf(tool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(harness.session, FixtureEngine(), harness.registry, {}, {})
                }
                onNodeWithTag("editor-tool-adjust").performClick()
                onNodeWithTag("editor-inspector").assertIsDisplayed()

                harness.settings.setEditorToolEnabled("adjust", enabled = false)
                waitForIdle()

                onNodeWithTag("editor-inspector").assertDoesNotExist()
                onNodeWithTag("editor-tool-adjust").assertDoesNotExist()
            }
        }
    }

    @Test
    fun `the skipped notice appears when a committed op's tool is switched off`() {
        val tool = FixtureTool(id = "adjust", domain = ToolDomain.Color)
        onEditor(listOf(tool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(harness.session, FixtureEngine(), harness.registry, {}, {})
                }
                harness.apply(ToolOp("adjust", tool.defaults()), ToolCardinality.Single)
                harness.settings.setEditorToolEnabled("adjust", enabled = false)
                waitForIdle()

                onNodeWithTag("editor-skipped-notice").assertIsDisplayed()
                onNodeWithText("1 operation was not applied").assertIsDisplayed()
            }
        }
    }

    /** Bucket B says so before any work is done; bucket A has nothing to warn about. */
    @Test
    fun `the no-history notice appears for a format with no carrier and for no other`() {
        val tool = FixtureTool(id = "adjust", domain = ToolDomain.Color)
        onEditor(listOf(tool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(
                        harness.session,
                        FixtureEngine(capability = EditCapability.Destructive),
                        harness.registry,
                        {},
                        {}
                    )
                }
                onNodeWithTag("editor-no-history-notice").assertIsDisplayed()
            }
        }
        onEditor(listOf(tool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(harness.session, FixtureEngine(), harness.registry, {}, {})
                }
                onNodeWithTag("editor-no-history-notice").assertDoesNotExist()
            }
        }
    }
}
