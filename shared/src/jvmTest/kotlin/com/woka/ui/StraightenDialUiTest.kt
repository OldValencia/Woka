package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import com.woka.feature.editor.shell.DIAL_DEGREE
import com.woka.feature.editor.shell.paramsOf
import com.woka.feature.editor.tools.straighten.StraightenTool
import com.woka.support.EditorHarness
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

/**
 * Straighten's dial (RD-205, `.dial`): the scale follows the pointer 8 dp to the degree, a
 * drag is one undo step, near zero it lands on zero, and the keys step it.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class StraightenDialUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private val dial = "param-${StraightenTool.Angle.key}"

    private fun EditorHarness.angle() = paramsOf(session.document.value, StraightenTool)[StraightenTool.Angle]

    @Test
    fun `a drag left turns the value up a degree per step and is one undo`() {
        onEditor(listOf(StraightenTool)) { harness ->
            onUi {
                setContent { ParamPanel(StraightenTool, harness) }
                val degree = with(density) { DIAL_DEGREE.toPx() }
                onNodeWithTag(dial).performMouseInput {
                    moveTo(center)
                    press()
                    moveTo(center - Offset(degree * 5f, 0f))
                    moveTo(center - Offset(degree * 10f, 0f))
                    release()
                }
                waitForIdle()

                assertEquals(10f, harness.angle(), 0.05f)
                assertNull(harness.session.document.value.pending, "the drag left work in flight")
                onNodeWithTag("$dial-readout").assertTextEquals("10.0°")
                harness.session.undo()
                assertEquals(0f, harness.angle(), "a drag was more than one step")
            }
        }
    }

    @Test
    fun `a drag ending within nine tenths of a degree lands on zero`() {
        onEditor(listOf(StraightenTool)) { harness ->
            onUi {
                setContent { ParamPanel(StraightenTool, harness) }
                val degree = with(density) { DIAL_DEGREE.toPx() }
                onNodeWithTag(dial).performMouseInput {
                    moveTo(center)
                    press()
                    moveTo(center + Offset(degree * 3f, 0f))
                    moveTo(center + Offset(degree * 0.5f, 0f))
                    release()
                }
                waitForIdle()

                assertEquals(0f, harness.angle())
                assertNull(harness.session.document.value.pending, "a drag back to zero was left in flight")
            }
        }
    }

    @Test
    fun `arrows step half a degree, Shift five, Home goes back, a double-click resets`() {
        onEditor(listOf(StraightenTool)) { harness ->
            onUi {
                setContent { ParamPanel(StraightenTool, harness) }
                onNodeWithTag(dial).performClick()
                onNodeWithTag(dial).performKeyInput { pressKey(Key.DirectionRight) }
                waitForIdle()
                assertEquals(0.5f, harness.angle())

                onNodeWithTag(dial).performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.DirectionLeft) } }
                waitForIdle()
                assertEquals(-4.5f, harness.angle())

                onNodeWithTag(dial).performKeyInput { pressKey(Key.MoveHome) }
                waitForIdle()
                assertEquals(0f, harness.angle())

                onNodeWithTag(dial).performKeyInput { pressKey(Key.DirectionRight) }
                onNodeWithTag(dial).performMouseInput { doubleClick() }
                waitForIdle()
                assertEquals(0f, harness.angle())
                assertNull(harness.session.document.value.pending)
            }
        }
    }
}
