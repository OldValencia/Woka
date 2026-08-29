package com.woka.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.shell.EditorScreen
import com.woka.feature.editor.shell.paramsOf
import com.woka.support.EditorHarness
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
 * `−  0  +` and everything it opens (ME-155).
 *
 * The frame carries three gestures at once and each is a different code path: the ends
 * write directly, the middle raises a popover, and the right-click writes without raising
 * anything. The last is the one worth a test of its own — it is the only control gesture
 * in the app with no visible affordance, so nothing else would notice if it stopped
 * working.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ParamStepperUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val tool = FixtureTool(id = "adjust", domain = ToolDomain.Color)

    private val key = FixtureParams.Amount.key

    private fun EditorHarness.amount() =
        paramsOf(session.document.value, tool)[FixtureParams.Amount]

    @Test
    fun `an end steps the value and stops at the range rather than clamping to it`() {
        onEditor(listOf(tool)) { harness ->
            onUi {
                openTool(harness)

                // `Amount` opens at 0 in a -1..1 range, so one press is the ceiling.
                onNodeWithTag("param-$key-plus").performClick()
                waitForIdle()
                assertEquals(1f, harness.amount())

                // At the top it stops being pressable at all, rather than accepting
                // presses that write the same value back.
                onNodeWithTag("param-$key-plus").assertHasNoClickAction()
                onNodeWithTag("param-$key-minus").performClick()
                waitForIdle()
                assertEquals(0f, harness.amount())
            }
        }
    }

    @Test
    fun `the number opens the slider, the field and Reset — and Reset returns the detent`() {
        onEditor(listOf(tool)) { harness ->
            onUi {
                openTool(harness)
                onNodeWithTag("param-$key-plus").performClick()
                waitForIdle()

                onNodeWithTag("param-$key-value").performClick()
                waitForIdle()

                onNodeWithTag("param-$key-popover").assertIsDisplayed()
                onNodeWithTag("param-$key-slider").assertIsDisplayed()
                onNodeWithTag("param-$key-field").assertIsDisplayed()

                onNodeWithTag("param-$key-reset").performClick()
                waitForIdle()
                assertEquals(FixtureParams.Amount.detent, harness.amount())
            }
        }
    }

    /**
     * **The gesture with nothing drawn for it.** A right-click writes the default and
     * raises no popover — if it raised one, the shortcut would cost a second click to
     * dismiss and would be worth less than the menu it was meant to skip.
     */
    @Test
    fun `right-clicking the frame resets the value and opens nothing`() {
        onEditor(listOf(tool)) { harness ->
            onUi {
                openTool(harness)
                onNodeWithTag("param-$key-plus").performClick()
                waitForIdle()
                assertTrue(harness.amount() != FixtureParams.Amount.detent)

                onNodeWithTag("param-$key").performMouseInput { rightClick() }
                waitForIdle()

                assertEquals(FixtureParams.Amount.detent, harness.amount())
                onNodeWithTag("param-$key-popover").assertDoesNotExist()
            }
        }
    }

    private fun ComposeUiTest.openTool(harness: EditorHarness) {
        setContent {
            EditorScreen(harness.session, FixtureEngine(), harness.registry, {}, {})
        }
        onNodeWithTag("editor-tool-adjust").performClick()
        waitForIdle()
    }
}
