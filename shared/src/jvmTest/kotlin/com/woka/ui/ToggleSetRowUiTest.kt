package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.shell.EditorScreen
import com.woka.support.FixtureChoice
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

/**
 * The row a `ChoiceParam` cannot draw (ME-147), through the real inspector.
 *
 * Against `FixtureTool` rather than against markup, because the claim being tested is
 * the *schema's* — that a spec can hold several of a set at once and get them into the
 * document. Markup's four faces are ME-151 and are one caller of this, not the proof of
 * it.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ToggleSetRowUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val tool = FixtureTool(id = "adjust", domain = ToolDomain.Color)

    private val spec = FixtureParams.Toggles

    private fun tagOf(option: FixtureChoice) = "param-${spec.key}-${option.name}"

    @Test
    fun `two options can be lit at once and both reach the document`() {
        onEditor(listOf(tool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(harness.session, FixtureEngine(), harness.registry, {}, {})
                }
                onNodeWithTag("editor-tool-adjust").performClick()

                onNodeWithTag("param-${spec.key}").assertIsDisplayed()
                FixtureChoice.entries.forEach { onNodeWithTag(tagOf(it)).assertIsDisplayed() }

                onNodeWithTag(tagOf(FixtureChoice.First)).performClick()
                waitForIdle()
                onNodeWithTag(tagOf(FixtureChoice.Third)).performClick()
                waitForIdle()
            }

            assertEquals(
                setOf(FixtureChoice.First, FixtureChoice.Third),
                harness.session.document.value.ops.single().params[spec]
            )
            // A press is a press: two chips, two history entries — asserted by taking
            // one back, since the history itself is the session's own. The fixture is
            // `Continuous`, and a chip settles at once because there is no drag to be in
            // the middle of.
            harness.undo()
            assertEquals(
                setOf(FixtureChoice.First),
                harness.session.document.value.ops.single().params[spec]
            )
        }
    }

    @Test
    fun `clicking a lit chip clears that one and leaves the other`() {
        onEditor(listOf(tool)) { harness ->
            onUi {
                setContent {
                    EditorScreen(harness.session, FixtureEngine(), harness.registry, {}, {})
                }
                onNodeWithTag("editor-tool-adjust").performClick()

                onNodeWithTag(tagOf(FixtureChoice.First)).performClick()
                waitForIdle()
                onNodeWithTag(tagOf(FixtureChoice.Second)).performClick()
                waitForIdle()
                onNodeWithTag(tagOf(FixtureChoice.First)).performClick()
                waitForIdle()
            }

            assertEquals(
                setOf(FixtureChoice.Second),
                harness.session.document.value.ops.single().params[spec]
            )
        }
    }
}
