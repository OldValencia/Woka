package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import com.woka.feature.editor.shell.paramsOf
import com.woka.support.EditorHarness
import com.woka.support.FixtureParams
import com.woka.support.FixtureTool
import com.woka.support.MainDispatcherRule
import com.woka.support.ParamPanel
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A level as one scrub row (RD-202): the row is the slider, the number is typed, the default
 * holds the value as it passes. `Amount` opens at 0 in a -1..1 range.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ScrubRowUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val tool = FixtureTool()
    private val row = "param-${FixtureParams.Amount.key}"

    private fun EditorHarness.amount() = paramsOf(session.document.value, tool)[FixtureParams.Amount]

    @Test
    fun `a press sets the value where it lands, as one step`() {
        onEditor(listOf(tool)) { harness ->
            onUi {
                setContent { ParamPanel(tool, harness) }
                onNodeWithTag(row).performMouseInput { click(Offset(width * 0.75f, centerY)) }
                waitForIdle()

                assertEquals(0.5f, harness.amount(), 0.02f)
                assertNull(harness.session.document.value.pending, "the press left work in flight")
                assertTrue(harness.session.canUndo.value)
            }
        }
    }

    @Test
    fun `a drag follows the pointer and settles once on release`() {
        onEditor(listOf(tool)) { harness ->
            onUi {
                setContent { ParamPanel(tool, harness) }
                onNodeWithTag(row).performMouseInput {
                    moveTo(Offset(width * 0.1f, centerY))
                    press()
                    moveTo(Offset(width * 0.5f, centerY))
                    moveTo(Offset(width * 0.9f, centerY))
                    release()
                }
                waitForIdle()

                assertEquals(0.8f, harness.amount(), 0.02f)
                assertNull(harness.session.document.value.pending)
                harness.session.undo()
                assertEquals(FixtureParams.Amount.default, harness.amount(), "a drag was more than one step")
            }
        }
    }

    @Test
    fun `near the default the value sticks to it`() {
        onEditor(listOf(tool)) { harness ->
            onUi {
                setContent { ParamPanel(tool, harness) }
                onNodeWithTag(row).performMouseInput { click(Offset(width * 0.9f, centerY)) }
                onNodeWithTag(row).performMouseInput { click(Offset(width * 0.505f, centerY)) }
                waitForIdle()

                assertEquals(FixtureParams.Amount.detent, harness.amount())
            }
        }
    }

    @Test
    fun `a typed number is written on Enter, and right-click puts it back`() {
        onEditor(listOf(tool)) { harness ->
            onUi {
                setContent { ParamPanel(tool, harness) }
                onNodeWithTag("$row-field").performClick()
                onNodeWithTag("$row-field").performTextReplacement("0.4")
                onNodeWithTag("$row-field").performKeyInput { pressKey(Key.Enter) }
                waitForIdle()
                assertEquals(0.4f, harness.amount())
                assertNull(harness.session.document.value.pending, "Enter did not settle the field")

                onNodeWithTag(row).performMouseInput { rightClick() }
                waitForIdle()
                assertEquals(FixtureParams.Amount.detent, harness.amount())
            }
        }
    }
}
