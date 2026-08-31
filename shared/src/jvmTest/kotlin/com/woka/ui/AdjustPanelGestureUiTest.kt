package com.woka.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import com.woka.feature.editor.tools.adjust.AdjustTool
import com.woka.support.MainDispatcherRule
import com.woka.support.ParamPanel
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test

/** Far enough past the touch slop that `draggable` claims the gesture. */
private const val DRAG_PX = 80f

/**
 * The two gestures the chooser answers, both asked for by hand 2026-09-01.
 *
 * They are one subject: the primary button pans the strip and the secondary resets a
 * chip, and the pair only works because they never contend. An earlier reading had the
 * *right* button doing both, which cannot work — the strip is made of chips, so every
 * attempt to drag would have begun by resetting whatever it started on.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class AdjustPanelGestureUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /**
     * A drag must not read as a click (asked for by hand 2026-09-01).
     *
     * `draggable` claims the gesture only past the touch slop and a chip's `clickable`
     * takes anything shorter, so the assertion is that a press which *travels* leaves the
     * selection where it was. Without that rule the strip would change the open
     * adjustment every time it was pushed sideways.
     */
    @Test
    fun `dragging the strip does not select the chip it started on`() {
        onEditor(listOf(AdjustTool)) { harness ->
            onUi {
                setContent { ParamPanel(AdjustTool, harness) }
                waitForIdle()

                onNodeWithTag("param-chip-warmth").performMouseInput {
                    press(MouseButton.Primary)
                    moveBy(Offset(-DRAG_PX, 0f))
                    release(MouseButton.Primary)
                }
                waitForIdle()

                onNodeWithTag("param-exposure-slider").assertIsDisplayed()
                onNodeWithTag("param-warmth-slider").assertDoesNotExist()
            }
        }
    }

    /** Right-click puts an adjustment back — the rule the rest of the editor already has. */
    @Test
    fun `right-clicking a chip resets that adjustment`() {
        onEditor(listOf(AdjustTool)) { harness ->
            onUi {
                setContent { ParamPanel(AdjustTool, harness) }
                onNodeWithTag("param-exposure-field").performTextReplacement("40")
                waitForIdle()

                // Pressed and released explicitly: `resetOnRightClick` acts on the way
                // down, and this version of the test API has no `rightClick()`.
                onNodeWithTag("param-chip-exposure").performMouseInput {
                    moveTo(center)
                    press(MouseButton.Secondary)
                    release(MouseButton.Secondary)
                }
                waitForIdle()

                onNodeWithTag("param-chip-exposure-readout", useUnmergedTree = true)
                    .assertTextEquals("0")
            }
        }
    }
}
