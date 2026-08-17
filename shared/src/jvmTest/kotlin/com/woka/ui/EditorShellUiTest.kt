package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import kotlin.test.assertEquals

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
                        fileName = "photo.jpg",
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
                    EditorScreen(harness.session, FixtureEngine(), harness.registry, "p.jpg", {}, {})
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
                    EditorScreen(harness.session, FixtureEngine(), harness.registry, "p.jpg", {}, {})
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
    fun `the dirty mark appears only after a commit`() {
        val tool = FixtureTool(id = "adjust", domain = ToolDomain.Color)
        onEditor(listOf(tool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(harness.session, FixtureEngine(), harness.registry, "p.jpg", {}, {})
                }
                onNodeWithTag("editor-dirty").assertDoesNotExist()

                harness.apply(ToolOp("adjust", tool.defaults()), ToolCardinality.Single)
                waitForIdle()

                onNodeWithTag("editor-dirty").assertIsDisplayed()
            }
        }
    }

    @Test
    fun `cancel and done fire exactly once`() {
        var cancels = 0
        var dones = 0
        onEditor(listOf(FixtureTool())) { harness ->
            onUi {
                setContent {
                    EditorScreen(
                        harness.session, FixtureEngine(), harness.registry, "p.jpg",
                        onCancel = { cancels++ },
                        onDone = { dones++ }
                    )
                }
                onNodeWithTag("editor-cancel").performClick()
                onNodeWithTag("editor-done").performClick()
            }
        }
        assertEquals(1, cancels)
        assertEquals(1, dones)
    }

    @Test
    fun `the skipped notice appears when a committed op's tool is switched off`() {
        val tool = FixtureTool(id = "adjust", domain = ToolDomain.Color)
        onEditor(listOf(tool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(harness.session, FixtureEngine(), harness.registry, "p.jpg", {}, {})
                }
                harness.apply(ToolOp("adjust", tool.defaults()), ToolCardinality.Single)
                harness.settings.setEditorToolEnabled("adjust", enabled = false)
                waitForIdle()

                onNodeWithTag("editor-skipped-notice").assertIsDisplayed()
                onNodeWithText("1 operation was not applied").assertIsDisplayed()
            }
        }
    }
}
