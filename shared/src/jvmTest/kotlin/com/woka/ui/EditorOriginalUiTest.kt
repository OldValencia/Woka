package com.woka.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.woka.app.shell.GlobalShortcutState
import com.woka.app.shell.TopBarAccessoriesState
import com.woka.core.ui.components.OriginalCompare
import com.woka.feature.editor.shell.EditorKeyEffect
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The editor's `Original` is the viewer's control (RD-502): a click toggles it with the label
 * over the canvas, a hold returns on release, `\` held peeks, and Esc ends a comparison before
 * it leaves the editor. Until RD-502 it was hold-only.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorOriginalUiTest {

    private val keyTarget = "editor-key-target"

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `a click toggles the original and its label, a hold returns on release`() {
        onEditor(listOf(FixtureTool(id = "filter"))) { harness ->
            onUi {
                setContent { EditorUnderShell(harness, TopBarAccessoriesState()) }
                waitForIdle()
                onNodeWithTag("original-label").assertDoesNotExist()

                onNodeWithTag("editor-peek-original").performClick()
                waitForIdle()
                onNodeWithTag("original-label").assertExists()
                onNodeWithTag("editor-peek-original").performClick()
                waitForIdle()
                onNodeWithTag("original-label").assertDoesNotExist()

                onNodeWithTag("editor-peek-original").performMouseInput { press() }
                mainClock.advanceTimeBy(400)
                onNodeWithTag("original-label").assertExists()
                onNodeWithTag("editor-peek-original").performMouseInput { release() }
                waitForIdle()
                onNodeWithTag("original-label").assertDoesNotExist()
            }
        }
    }

    @Test
    fun `backslash held peeks and Esc ends a comparison before leaving`() {
        val shortcuts = GlobalShortcutState()
        val original = OriginalCompare()
        var cancelled = 0
        onUi {
            setContent {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                EditorKeyEffect(shortcuts, false, false, { cancelled++ }, {}, {}, {}, original = original)
                Box(
                    Modifier
                        .size(40.dp)
                        .testTag(keyTarget)
                        .focusRequester(focus)
                        .focusable()
                        .onPreviewKeyEvent { shortcuts.onPreviewKey?.invoke(it) ?: false }
                )
            }
            onNodeWithTag(keyTarget).performKeyInput { keyDown(Key.Backslash) }
            assertTrue(original.active, "a held backslash did not show the original")
            onNodeWithTag(keyTarget).performKeyInput { keyUp(Key.Backslash) }
            assertFalse(original.active, "releasing backslash did not return")

            original.toggle()
            onNodeWithTag(keyTarget).performKeyInput { pressKey(Key.Escape) }
            assertFalse(original.active)
            assertEquals(0, cancelled, "Esc left the editor over a comparison")
            onNodeWithTag(keyTarget).performKeyInput { pressKey(Key.Escape) }
            assertEquals(1, cancelled)
        }
    }
}
