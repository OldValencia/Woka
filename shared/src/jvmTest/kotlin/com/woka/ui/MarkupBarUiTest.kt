package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import com.woka.feature.editor.shell.paramsOf
import com.woka.feature.editor.tools.markup.MarkupTool
import com.woka.support.EditorHarness
import com.woka.support.MainDispatcherRule
import com.woka.support.chooseMarkupPen
import com.woka.support.onEditor
import com.woka.support.onUi
import com.woka.support.openMarkupTool
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Markup as two compact lines (ME-155): the pen above, and a bar of buttons under it that
 * carries the whole of its style.
 *
 * **What the pen decides is unchanged** — `MarkupRowVisibilityUiTest` still owns that
 * rule. This is about the *bar*: that the controls it holds are the collapsed ones, that
 * the plate's palette and its `Corner` are behind one button, and that a right-click on
 * that button clears the plate without opening it.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class MarkupBarUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun EditorHarness.fill() =
        paramsOf(session.document.value, MarkupTool)[MarkupTool.Fill]

    @Test
    fun `a stroke pen carries two buttons and a caption carries four`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openMarkupTool(harness)
                onNodeWithTag("param-${MarkupTool.Color.key}").assertIsDisplayed()
                onNodeWithTag("param-${MarkupTool.Width.key}").assertIsDisplayed()
                onNodeWithTag("param-${MarkupTool.Faces.key}").assertDoesNotExist()
                onNodeWithTag("param-${MarkupTool.Fill.key}").assertDoesNotExist()

                chooseMarkupPen("Text")
                onNodeWithTag("param-${MarkupTool.Color.key}").assertIsDisplayed()
                onNodeWithTag("param-${MarkupTool.TextSize.key}").assertIsDisplayed()
                onNodeWithTag("param-${MarkupTool.Faces.key}").assertIsDisplayed()
                onNodeWithTag("param-${MarkupTool.Fill.key}").assertIsDisplayed()
                onNodeWithTag("param-${MarkupTool.Width.key}").assertDoesNotExist()
            }
        }
    }

    /** A plate's roundness is a row of its own once there is a plate to round (RD-203). */
    @Test
    fun `the corner appears under the plate once a plate is chosen`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openMarkupTool(harness)
                chooseMarkupPen("Text")
                onNodeWithTag("param-${MarkupTool.Fill.key}-swatch-0").assertIsDisplayed()
                // Still nothing to round, so still no row — ME-146's rule.
                onNodeWithTag("param-${MarkupTool.Corner.key}").assertDoesNotExist()

                onNodeWithTag("param-${MarkupTool.Fill.key}-swatch-1").performClick()
                waitForIdle()
                onNodeWithTag("param-${MarkupTool.Corner.key}").assertIsDisplayed()
            }
        }
    }

    @Test
    fun `right-clicking the plate's swatches clears the plate`() {
        onEditor(listOf(MarkupTool)) { harness ->
            onUi {
                openMarkupTool(harness)
                chooseMarkupPen("Text")
                onNodeWithTag("param-${MarkupTool.Fill.key}-swatch-1").performClick()
                waitForIdle()
                assertTrue(harness.fill() != 0L, "the plate was never set")

                onNodeWithTag("param-${MarkupTool.Fill.key}").performMouseInput { rightClick() }
                waitForIdle()
                assertEquals(0L, harness.fill(), "the plate survived a right-click")
            }
        }
    }
}
