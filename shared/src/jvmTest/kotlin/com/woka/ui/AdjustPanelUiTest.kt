package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.tools.adjust.AdjustTool
import com.woka.support.FixtureTool
import com.woka.support.MainDispatcherRule
import com.woka.support.ParamPanel
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test

/**
 * The adjustments panel as one chooser over one control (ME-161).
 *
 * The shape is the subject, so the assertions are about *what is drawn*, not about what
 * a value became: six labelled rows would pass every existing Adjust test and still be
 * the 613 dp wall this ticket exists to remove.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class AdjustPanelUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `six adjustments draw one chooser and one slider, not six rows`() {
        onEditor(listOf(AdjustTool)) { harness ->
            onUi {
                setContent { ParamPanel(AdjustTool, harness) }
                waitForIdle()

                onNodeWithTag("param-chooser").assertIsDisplayed()
                onNodeWithTag("param-chip-exposure").assertIsDisplayed()
                onNodeWithTag("param-chip-tint").assertIsDisplayed()

                // The whole point: one control, whatever the run's length.
                onNodeWithTag("param-exposure-slider").assertIsDisplayed()
                onNodeWithTag("param-contrast-slider").assertDoesNotExist()
                onNodeWithTag("param-tint-slider").assertDoesNotExist()
            }
        }
    }

    @Test
    fun `picking a chip moves the control to it`() {
        onEditor(listOf(AdjustTool)) { harness ->
            onUi {
                setContent { ParamPanel(AdjustTool, harness) }
                onNodeWithTag("param-chip-warmth").performClick()
                waitForIdle()

                onNodeWithTag("param-warmth-slider").assertIsDisplayed()
                onNodeWithTag("param-exposure-slider").assertDoesNotExist()
            }
        }
    }

    /**
     * Every chip carries its own number, which is how an edit stays visible while its
     * control is closed — the thing a chooser costs if the chips are names alone.
     *
     * `useUnmergedTree`, because a chip is `clickable` and Compose merges its children's
     * semantics into it: the readout is there, and a merged finder cannot see it.
     */
    @Test
    fun `a chip reports the value of the adjustment it names`() {
        onEditor(listOf(AdjustTool)) { harness ->
            onUi {
                setContent { ParamPanel(AdjustTool, harness) }
                onNodeWithTag("param-exposure-field").performTextReplacement("40")
                waitForIdle()

                onNodeWithTag("param-chip-exposure-readout", useUnmergedTree = true)
                    .assertTextEquals("+40")
            }
        }
    }

    /**
     * The threshold, from the other side. `FixtureTool` declares one float among specs of
     * every other kind, so its run never reaches four and must still draw as a labelled
     * row — which is what says ME-161 did not quietly reshape every tool with a slider.
     */
    @Test
    fun `a tool with too few sliders still draws labelled rows`() {
        val tool = FixtureTool()
        onEditor(listOf(tool)) { harness ->
            onUi {
                setContent { ParamPanel(tool, harness) }
                waitForIdle()

                onNodeWithTag("param-chooser").assertDoesNotExist()
            }
        }
    }
}
