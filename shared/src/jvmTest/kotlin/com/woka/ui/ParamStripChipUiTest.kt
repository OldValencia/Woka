package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.woka.core.ui.icons.Icons
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.shell.StripChip
import com.woka.feature.editor.tools.adjust.AdjustTool
import com.woka.support.MainDispatcherRule
import com.woka.support.ParamPanel
import com.woka.support.onEditor
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test

private val SPAN = -100f..100f

/**
 * A chip draws its name always, and a glyph beside it where one has been drawn (ME-144).
 *
 * **Glyph instead of name was tried and refused on screen** (2026-09-01). It is the same
 * answer ME-109 got from the rail: an adjustment is told apart by its name, and the
 * picture beside it is what makes the strip scannable rather than what makes it short.
 * A spec with no glyph still draws, which is what let the art arrive a file at a time.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ParamStripChipUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `a chip with no glyph draws its name`() = onUi {
        val spec = FloatParam("exposure", "Exposure", SPAN, default = 0f)
        setContent { StripChip(spec, 0f, selected = false, onReset = {}, onClick = {}) }
        waitForIdle()

        onNodeWithText("Exposure").assertIsDisplayed()
    }

    @Test
    fun `a chip with a glyph draws the glyph and the name`() = onUi {
        val spec =
            FloatParam("exposure", "Exposure", SPAN, default = 0f, icon = Icons.Static.Adjust)
        setContent { StripChip(spec, 0f, selected = false, onReset = {}, onClick = {}) }
        waitForIdle()

        onNodeWithText("Exposure").assertIsDisplayed()
        onNodeWithTag("param-chip-exposure-readout", useUnmergedTree = true).assertIsDisplayed()
    }

    /** The chip carries the name, so nothing repeats it above the slider. */
    @Test
    fun `no name is drawn above the control`() {
        onEditor(listOf(AdjustTool)) { harness ->
            onUi {
                setContent { ParamPanel(AdjustTool, harness) }
                waitForIdle()

                onNodeWithTag("param-selected-name").assertDoesNotExist()
                onNodeWithText("Exposure").assertIsDisplayed()
            }
        }
    }
}
