package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.woka.app.shell.GlobalShortcutState

/**
 * The editor's keyboard, and the handler it has to give back.
 *
 * **`GlobalShortcutState.onPreviewKey` is a single mutable slot**, and the viewer
 * installs its own handler in a `DisposableEffect(globalShortcuts)` that will *not*
 * re-run when this one overwrites it. So the previous handler is captured on entry and
 * **restored on dispose** — without that, Esc, Space and the arrows stop working in the
 * viewer after the first edit and stay broken until the app restarts. That is the
 * failure a user reports as "the arrow keys died", days after the edit that caused it.
 *
 * Fresh values reach the handler through [rememberUpdatedState], so the effect
 * registers once and the slot is written once.
 */
@Composable
internal fun EditorKeyEffect(
    globalShortcuts: GlobalShortcutState,
    canUndo: Boolean,
    canRedo: Boolean,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDone: () -> Unit
) {
    val curCancel by rememberUpdatedState(onCancel)
    val curUndo by rememberUpdatedState(onUndo)
    val curRedo by rememberUpdatedState(onRedo)
    val curDone by rememberUpdatedState(onDone)
    val curCanUndo by rememberUpdatedState(canUndo)
    val curCanRedo by rememberUpdatedState(canRedo)

    DisposableEffect(globalShortcuts) {
        val previous = globalShortcuts.onPreviewKey
        globalShortcuts.onPreviewKey = handler@{ event ->
            if (event.type != KeyEventType.KeyDown) return@handler false
            when {
                event.key == Key.Escape -> consume { curCancel() }
                event.isCtrlPressed && event.key == Key.S -> consume { curDone() }
                // Both spellings of redo, because both are muscle memory and neither
                // is wrong. Checked before undo: `Ctrl+Shift+Z` also matches the undo
                // predicate below.
                event.isCtrlPressed && event.isShiftPressed && event.key == Key.Z ->
                    consumeIf(curCanRedo) { curRedo() }
                event.isCtrlPressed && event.key == Key.Y -> consumeIf(curCanRedo) { curRedo() }
                event.isCtrlPressed && event.key == Key.Z -> consumeIf(curCanUndo) { curUndo() }
                else -> false
            }
        }
        onDispose { globalShortcuts.onPreviewKey = previous }
    }
}

private inline fun consume(action: () -> Unit): Boolean {
    action()
    return true
}

/**
 * Consumed either way. A `Ctrl+Z` with nothing to undo must not fall through to
 * whatever the viewer underneath does with it — the editor owns the keyboard while it
 * is open, including for the keys it currently has no work for.
 */
private inline fun consumeIf(enabled: Boolean, action: () -> Unit): Boolean {
    if (enabled) action()
    return true
}
