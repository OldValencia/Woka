package com.woka.ui

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import com.woka.app.shell.GlobalShortcutState
import com.woka.feature.editor.shell.EditorKeyEffect
import com.woka.support.MainDispatcherRule
import com.woka.support.onUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
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
