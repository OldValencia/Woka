package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.shell.EditorScreen
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The renderers, driven through the real inspector.
 *
 * `FixtureTool` declares one spec of every type, so opening it is the whole matrix in
 * one composition — which is the point of having built it in ME-011.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ParamRowUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val tool = FixtureTool(id = "adjust", domain = ToolDomain.Color)

    @Test
    fun `every spec type that has a renderer draws a row, and the one that does not draws none`() {
        onEditor(listOf(tool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(harness.session, FixtureEngine(), harness.registry, {}, {})
                }
                onNodeWithTag("editor-tool-adjust").performClick()

                listOf(
                    FixtureParams.Amount, FixtureParams.Count, FixtureParams.Flag,
                    FixtureParams.Choice, FixtureParams.Tint, FixtureParams.Press,
                    // ME-112 gave `PreviewChoiceParam` its strip; until there were
                    // filters to preview it drew nothing.
                    FixtureParams.Preview
                ).forEach { spec ->
                    onNodeWithTag("param-${spec.key}").assertIsDisplayed()
                }

                // `ObjectParam` belongs to an overlay — nothing rather than an empty
                // placeholder, and the last spec type the inspector answers with `Unit`.
                onNodeWithTag("param-${FixtureParams.Payload.key}").assertDoesNotExist()
            }
        }
    }

    @Test
    fun `toggling the switch writes through to the document`() {
        onEditor(listOf(tool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(harness.session, FixtureEngine(), harness.registry, {}, {})
                }
                onNodeWithTag("editor-tool-adjust").performClick()
                onNodeWithTag("param-${FixtureParams.Flag.key}").performClick()
                waitForIdle()
            }
            val op = harness.session.document.value.ops.single()
            assertTrue(op.params[FixtureParams.Flag])
            // The fixture is Continuous, and a switch has no drag — so it settles at
            // once and produces exactly one history entry.
            assertEquals(1, harness.session.document.value.ops.size)
            assertTrue(harness.session.canUndo.value)
        }
    }

    @Test
    fun `a stepper press moves the value by one and cannot leave the range`() {
        val key = FixtureParams.Count.key
        onEditor(listOf(tool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(harness.session, FixtureEngine(), harness.registry, {}, {})
                }
                onNodeWithTag("editor-tool-adjust").performClick()

                // `Count` opens at 1 in a 0..10 range.
                onNodeWithTag("param-$key-plus").performClick()
                waitForIdle()
                assertEquals(2, harness.session.document.value.ops.single().params[FixtureParams.Count])

                // Two presses down from 2 would reach 0; a third must not reach -1.
                repeat(3) {
                    onNodeWithTag("param-$key-minus").performClick()
                    waitForIdle()
                }
                assertEquals(0, harness.session.document.value.ops.single().params[FixtureParams.Count])

                // At the floor it stops being pressable at all, rather than accepting
                // presses that clamp to the same value.
                onNodeWithTag("param-$key-minus").assertHasNoClickAction()
            }
        }
    }
}
