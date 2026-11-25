package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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
    fun `the inspector has one tab per domain and opens on the first`() {
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
                    onNodeWithTag("editor-tab-${it.name.lowercase()}").assertIsDisplayed()
                }
                // One tab is always open (RD-201): the first, which is a film's Clip.
                onAllNodesWithTag("editor-inspector").assertCountEquals(1)
                onNodeWithTag("editor-tab-time").assertIsSelected()
            }
        }
    }

    @Test
    fun `clicking a tab switches the inspector to it`() {
        onEditor(twoTabs()) { harness ->
            onUi {
                setContent {
                    EditorScreen(harness.session, FixtureEngine(), harness.registry, {}, {})
                }
                onNodeWithTag("editor-tab-adjust").assertIsSelected()

                onNodeWithTag("editor-tab-markup").performClick()
                onNodeWithTag("editor-tab-markup").assertIsSelected()
                onNodeWithTag("editor-tab-adjust").assertIsNotSelected()
                onAllNodesWithTag("editor-inspector").assertCountEquals(1)
            }
        }
    }

    @Test
    fun `disabling the open tab's tool moves the inspector to the next tab`() {
        onEditor(twoTabs()) { harness ->
            onUi {
                setContent {
                    EditorScreen(harness.session, FixtureEngine(), harness.registry, {}, {})
                }
                onNodeWithTag("editor-tab-adjust").assertIsSelected()

                harness.settings.setEditorToolEnabled("adjust", enabled = false)
                waitForIdle()

                onNodeWithTag("editor-tab-adjust").assertDoesNotExist()
                onNodeWithTag("editor-tab-markup").assertIsSelected()
            }
        }
    }

    private fun twoTabs() = listOf(
        FixtureTool(id = "adjust", domain = ToolDomain.Color),
        FixtureTool(id = "markup", domain = ToolDomain.Annotate)
    )

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
