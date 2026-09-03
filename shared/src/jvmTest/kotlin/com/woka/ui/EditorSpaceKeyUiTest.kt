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

/**
 * The space bar, in its own file (ME-207).
 *
 * `EditorKeysUiTest` is about the handler the editor **borrows and gives back**, and this is about
 * one key reaching the canvas rather than the last button clicked. Apart because adding it there
 * pushed that file past the 150-line mandate, and the seam was free: neither test reads the other's
 * setup.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EditorSpaceKeyUiTest {

    /** The focused box standing in for the shell's root. */
    private val keyTarget = "editor-space-target"

    @get:Rule
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    /**
     * **The space bar reaches the canvas and not the last button clicked** (ME-207).
     *
     * Found by hand: a rail button keeps focus after it is clicked, so an unconsumed
     * space re-activated that tool instead of pausing the video. The handler is previewed
     * at the window root, before focus, which is why consuming it there is the fix — and
     * this asserts the consumption rather than the intent.
     */
    @Test
    fun `space asks the canvas to start or stop, and a typing tool keeps it`() {
        val shortcuts = GlobalShortcutState()
        val canvasKeys = CanvasKeys()
        var toggles = 0

        onUi {
            setContent {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                EditorKeyEffect(
                    shortcuts, false, false, {}, {}, {}, {},
                    onTogglePlayback = { toggles++ },
                    canvasKeys = canvasKeys
                )
                Box(
                    Modifier
                        .size(40.dp)
                        .testTag(keyTarget)
                        .focusRequester(focus)
                        .focusable()
                        .onPreviewKeyEvent { shortcuts.onPreviewKey?.invoke(it) ?: false }
                )
            }
            waitForIdle()

            onNodeWithTag(keyTarget).performKeyInput { pressKey(Key.Spacebar) }
            waitForIdle()
            assertEquals(1, toggles, "space did not reach the canvas")

            // A caption being typed into owns every key, this one included: a space in
            // "Somewhere near Batumi" must be a space.
            canvasKeys.typing = true
            onNodeWithTag(keyTarget).performKeyInput { pressKey(Key.Spacebar) }
            waitForIdle()
            assertEquals(1, toggles, "space was taken from a tool taking typed input")
        }
    }
}
