package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.woka.app.shell.TopBarAccessoriesState
import com.woka.feature.editor.shell.EditorHelpSheet
import com.woka.feature.editor.shell.HelpStep
import com.woka.support.EditorUnderShell
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
 * The `?` sheet (RD-401): numbered steps, each opening what it names — and no music step
 * until 1.1.0 (the author, 2026-11-26).
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorHelpSheetUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `a photo's sheet has four steps, each leading somewhere`() = onUi {
        val steps = mutableListOf<HelpStep>()
        setContent { EditorHelpSheet(video = false, onStep = { steps += it }, onDismiss = {}) }

        onNodeWithText("A quick photo edit").assertIsDisplayed()
        (1..4).forEach { onNodeWithTag("editor-help-step-$it").assertIsDisplayed() }
        onNodeWithTag("editor-help-step-2").performClick()
        onNodeWithTag("editor-help-step-4").performClick()
        assertEquals(listOf(HelpStep.Tool("adjust"), HelpStep.Copy), steps)
    }

    @Test
    fun `a film's sheet has no music step`() = onUi {
        val steps = mutableListOf<HelpStep>()
        setContent { EditorHelpSheet(video = true, onStep = { steps += it }, onDismiss = {}) }

        onNodeWithText("A short edit, step by step").assertIsDisplayed()
        onNodeWithText("2. Keep the edit or make a finished copy").assertIsDisplayed()
        onNodeWithTag("editor-help-step-3").assertDoesNotExist()
        onNodeWithText("music", substring = true).assertDoesNotExist()
        onNodeWithText("The line above the tabs", substring = true).assertIsDisplayed()
        onNodeWithTag("editor-help-step-1").performClick()
        assertEquals(listOf<HelpStep>(HelpStep.Clip), steps)
    }

    @Test
    fun `the header's question mark opens the sheet, and a step opens its tab`() {
        val tools = listOf(FixtureTool(id = "filter"), FixtureTool(id = "adjust"))
        onEditor(tools) { harness ->
            onUi {
                setContent { EditorUnderShell(harness, TopBarAccessoriesState()) }
                onNodeWithTag("editor-help-button").performClick()
                waitForIdle()
                onNodeWithText("A quick photo edit").assertIsDisplayed()

                onNodeWithText("2. Fine-tune light and colour").assertIsDisplayed()
                onNodeWithTag("editor-help-step-2").performClick()
                waitForIdle()
                onNodeWithText("A quick photo edit").assertDoesNotExist()
                onNodeWithTag("editor-tab-adjust").assertIsSelected()
            }
        }
    }
}
