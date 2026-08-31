package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * Light and Colour as two lists rather than one mixed one (ME-164).
 *
 * Reported by hand at eight adjustments, and the complaint was **not** the scrolling: it
 * was that Exposure sat beside Warmth for no reason a reader could see. So what is
 * asserted here is what each section *contains*, never how long the strip is.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class AdjustSectionUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `one section is shown at a time, and it is the first`() {
        onEditor(listOf(AdjustTool)) { harness ->
            onUi {
                setContent { ParamPanel(AdjustTool, harness) }
                waitForIdle()

                onNodeWithText("Light").assertIsDisplayed()
                onNodeWithText("Colour").assertIsDisplayed()

                onNodeWithTag("param-chip-exposure").assertIsDisplayed()
                onNodeWithTag("param-chip-warmth").assertDoesNotExist()
            }
        }
    }

    /**
     * The rule the section state exists for: without it the control under the strip goes
     * on setting a parameter from a tab nobody is looking at.
     */
    @Test
    fun `picking a section opens that section's first adjustment`() {
        onEditor(listOf(AdjustTool)) { harness ->
            onUi {
                setContent { ParamPanel(AdjustTool, harness) }
                onNodeWithText("Colour").performClick()
                waitForIdle()

                onNodeWithTag("param-chip-warmth").assertIsDisplayed()
                onNodeWithTag("param-chip-exposure").assertDoesNotExist()
                onNodeWithTag("param-saturation-slider").assertIsDisplayed()
                onNodeWithTag("param-exposure-slider").assertDoesNotExist()
            }
        }
    }

    /**
     * A tool whose floats declare no section draws what it drew before — which is what
     * says ME-164 reached Adjust and nothing else.
     */
    @Test
    fun `a tool with no sections draws no section control`() {
        val tool = FixtureTool()
        onEditor(listOf(tool)) { harness ->
            onUi {
                setContent { ParamPanel(tool, harness) }
                waitForIdle()

                onNodeWithText("Light").assertDoesNotExist()
                onNodeWithText("Colour").assertDoesNotExist()
            }
        }
    }
}
