package com.woka.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.woka.app.shell.TopBarAccessoriesState
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
 * Revert in the shell's **leading** slot, beside Back (ME-107).
 *
 * Two claims: it is drawn there at all — the slot is new, and a control nobody renders
 * is a control nobody can press — and it never acts on its own. The confirmation is
 * `EditorHost`'s, and this asserts the button only ever *asks*.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorRevertUiTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `the control sits beside Back and asks rather than acts`() {
        var asked = 0
        onEditor(listOf(FixtureTool())) { harness ->
            onUi {
                setContent {
                    EditorUnderShell(
                        harness = harness,
                        accessories = TopBarAccessoriesState(),
                        canRevert = true,
                        onRevert = { asked++ }
                    )
                }
                onNodeWithTag("editor-revert").assertIsDisplayed()
                onNodeWithTag("editor-revert").performClick()
            }
        }
        assertEquals(1, asked, "the button must open the confirmation, once")
    }

    /** Nothing to take back: the control stays in place and does nothing. */
    @Test
    fun `an unedited file cannot be reverted`() {
        var asked = 0
        onEditor(listOf(FixtureTool())) { harness ->
            onUi {
                setContent {
                    EditorUnderShell(
                        harness = harness,
                        accessories = TopBarAccessoriesState(),
                        canRevert = false,
                        onRevert = { asked++ }
                    )
                }
                onNodeWithTag("editor-revert").assertIsDisplayed()
                onNodeWithTag("editor-revert").performClick()
            }
        }
        assertEquals(0, asked, "a disabled Revert reverted")
    }
}
