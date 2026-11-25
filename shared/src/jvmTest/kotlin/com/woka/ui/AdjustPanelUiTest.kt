package com.woka.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import com.woka.feature.editor.shell.paramsOf
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
import kotlin.test.assertEquals

/**
 * The adjustments panel as the prototype draws it (RD-202): Auto-enhance, the sections, the
 * open section's levels as one well of scrub rows, and Reset adjustments under them.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class AdjustPanelUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `a section draws every one of its levels as a row in one well`() {
        onEditor(listOf(AdjustTool)) { harness ->
            onUi {
                setContent { ParamPanel(AdjustTool, harness) }
                waitForIdle()

                onNodeWithTag("param-well").assertIsDisplayed()
                listOf("exposure", "brightness", "contrast", "highlights", "shadows", "blackPoint").forEach {
                    onNodeWithTag("param-$it").assertIsDisplayed()
                }
                onNodeWithTag("param-warmth").assertDoesNotExist()
            }
        }
    }

    @Test
    fun `Auto-enhance stands above the rows and Reset adjustments under them`() {
        onEditor(listOf(AdjustTool)) { harness ->
            onUi {
                setContent { ParamPanel(AdjustTool, harness) }
                onNodeWithText("Auto-enhance").assertIsDisplayed()
                onNodeWithTag("param-exposure-field").performClick()
                onNodeWithTag("param-exposure-field").performTextReplacement("40")
                onNodeWithTag("param-exposure-field").performKeyInput { pressKey(Key.Enter) }
                waitForIdle()

                onNodeWithText("Reset adjustments").performClick()
                waitForIdle()
                assertEquals(0f, paramsOf(harness.session.document.value, AdjustTool)[AdjustTool.Exposure])
            }
        }
    }

    /** A tool without sections — every one but Adjust — gets its levels in a well and no control. */
    @Test
    fun `a tool without sections draws its level in a well`() {
        val tool = FixtureTool()
        onEditor(listOf(tool)) { harness ->
            onUi {
                setContent { ParamPanel(tool, harness) }
                waitForIdle()

                onNodeWithTag("param-well").assertIsDisplayed()
                onNodeWithText("Light").assertDoesNotExist()
            }
        }
    }
}
