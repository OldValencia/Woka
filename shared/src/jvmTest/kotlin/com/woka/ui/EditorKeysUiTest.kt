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
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.woka.app.shell.GlobalShortcutState
import com.woka.feature.editor.shell.CanvasKeys
import com.woka.feature.editor.shell.EditorKeyEffect
import com.woka.support.MainDispatcherRule
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The handler the editor borrows and has to give back.
 *
 * `GlobalShortcutState.onPreviewKey` is a **single mutable slot**, and the viewer
 * installs its own in a `DisposableEffect(globalShortcuts)` that does not re-run when
 * something overwrites it. So an editor that forgets to restore leaves the viewer
 * without Esc, Space or arrows — and the report arrives days later as "the arrow keys
 * stopped working", with nothing connecting it to an edit.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorKeysUiTest {

    /** The focused box standing in for the shell's root. */
    private val keyTarget = "editor-key-target"

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `the previous handler is put back when the editor leaves`() {
        val shortcuts = GlobalShortcutState()
        val sentinel: (KeyEvent) -> Boolean = { false }
        shortcuts.onPreviewKey = sentinel

        onUi {
            setContent { EditorKeyEffect(shortcuts, canUndo = false, canRedo = false, {}, {}, {}, {}) }
            waitForIdle()
            // While it is up, the slot is the editor's.
            assertTrue(shortcuts.onPreviewKey !== sentinel, "the editor never took the keyboard")
        }

        // And once the composition is gone, it is the viewer's again.
        assertSame(sentinel, shortcuts.onPreviewKey, "the editor kept the keyboard")
    }

    /**
     * **The keyboard is lent to a tool that is taking typed input** (ME-145).
     *
     * This handler is previewed at the window *root*, before focus reaches anything, which
     * is what lets `Esc` and `Delete` work over a canvas nobody has clicked — and is
     * exactly why a text field inside an overlay cannot live under it. With the gate shut,
     * `Backspace` deletes the caption being typed into instead of a character.
     *
     * Driven through a real `onPreviewKeyEvent` on a focused box, which is how `MainShell`
     * wires the slot: a test calling the handler directly would prove the `if` and not the
     * plumbing.
     */
    @Test
    fun `every key falls through while a tool is taking typed input`() {
        val shortcuts = GlobalShortcutState()
        val canvasKeys = CanvasKeys()
        var deleted = false
        canvasKeys.onDelete = { deleted = true; true }

        onUi {
            setContent {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                EditorKeyEffect(shortcuts, false, false, {}, {}, {}, {}, canvasKeys)
                Box(
                    Modifier
                        .size(40.dp)
                        .testTag(keyTarget)
                        .focusRequester(focus)
                        .focusable()
                        .onPreviewKeyEvent { shortcuts.onPreviewKey?.invoke(it) ?: false }
                )
            }
            // Shut: the editor owns Backspace and hands it to the open tool.
            onNodeWithTag(keyTarget).performKeyInput { pressKey(Key.Backspace) }
            waitForIdle()
            assertTrue(deleted, "the tool never saw the press")

            deleted = false
            canvasKeys.typing = true
            onNodeWithTag(keyTarget).performKeyInput { pressKey(Key.Backspace) }
            waitForIdle()
            assertFalse(deleted, "the tool deleted the shape being typed into")
        }
    }

    @Test
    fun `an empty slot is restored as empty rather than left holding the editor`() {
        val shortcuts = GlobalShortcutState()

        onUi {
            setContent {
                EditorKeyEffect(shortcuts, false, false, {}, {}, {}, {})
            }
            waitForIdle()
        }

        assertEquals(null, shortcuts.onPreviewKey)
    }
}
