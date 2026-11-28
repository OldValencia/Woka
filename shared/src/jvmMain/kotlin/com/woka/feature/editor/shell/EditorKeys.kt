package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.woka.app.shell.GlobalShortcutState
import com.woka.core.system.layoutFreeKey
import com.woka.core.ui.components.OriginalCompare
import com.woka.core.ui.components.holdOnKey

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
    onDone: () -> Unit,
    /**
     * Start or stop whatever moves on the canvas — a video's player, an animated still's
     * clock (ME-207). **Defaulted**, so the image editor is unchanged.
     */
    onTogglePlayback: () -> Unit = {},
    /**
     * `←`/`→` a second, with `Alt` a frame (ME-219; YouTube's arrows, the author 2026-11-05);
     * `false` leaves the key unconsumed.
     */
    onStep: (direction: Int, bySecond: Boolean) -> Boolean = { _, _ -> false },
    /**
     * The open canvas tool's `Delete`, through the editor's one keyboard slot (ME-114).
     * Null when no such tool is open, which is every tool but Markup today.
     */
    canvasKeys: CanvasKeys? = null,
    /** F6 shows or hides the inspector (RD-201, `#focus` in the prototype). */
    onTogglePanel: () -> Unit = {},
    /** The timeline's keys — `S`, `Home`, `End`, a clip's `Delete` (RD-301); `false` leaves one unconsumed. */
    onTimelineKey: (Key) -> Boolean = { false },
    /** `Original` — `\` holds it, Esc ends it before anything else (RD-502). */
    original: OriginalCompare = OriginalCompare()
) {
    val curCancel by rememberUpdatedState(onCancel)
    val curUndo by rememberUpdatedState(onUndo)
    val curRedo by rememberUpdatedState(onRedo)
    val curDone by rememberUpdatedState(onDone)
    val curCanUndo by rememberUpdatedState(canUndo)
    val curCanRedo by rememberUpdatedState(canRedo)
    val curTogglePlayback by rememberUpdatedState(onTogglePlayback)
    val curStep by rememberUpdatedState(onStep)
    val curCanvasKeys by rememberUpdatedState(canvasKeys)
    val curTogglePanel by rememberUpdatedState(onTogglePanel)
    val curTimelineKey by rememberUpdatedState(onTimelineKey)
    val curOriginal by rememberUpdatedState(original)

    DisposableEffect(globalShortcuts) {
        val previous = globalShortcuts.onPreviewKey
        val previousOwnsWindow = globalShortcuts.ownsWindow
        // **The window's keys too**, not only the focused subtree's: the editor owns the
        // keyboard while it is open, and a click on the picture leaves focus nowhere.
        globalShortcuts.ownsWindow = true
        globalShortcuts.onPreviewKey = handler@{ event ->
            // **Everything through, while a tool is taking typed input** (ME-145). This
            // handler is previewed at the window root, before focus, so without this
            // `Backspace` deletes the shape being typed into and `Esc` closes the editor
            // out from under the field. The field handles both itself.
            if (curCanvasKeys?.yields(event.key) == true) return@handler false
            // `\` is a hold, so its release counts as much as its press (RD-502).
            if (curOriginal.holdOnKey(event)) return@handler true
            if (event.type != KeyEventType.KeyDown) return@handler false
            when {
                // The original first (RD-502); then a film's selection, as the prototype's Esc
                // (RD-302); with neither, the editor.
                event.key == Key.Escape ->
                    consume { if (!curOriginal.endIfActive() && curCanvasKeys?.onBack?.invoke() != true) curCancel() }
                event.isCtrlPressed && event.key == Key.S -> consume { curDone() }
                // Both spellings of redo, because both are muscle memory and neither
                // is wrong. Checked before undo: `Ctrl+Shift+Z` also matches the undo
                // predicate below.
                event.isCtrlPressed && event.isShiftPressed && event.key == Key.Z ->
                    consumeIf(curCanRedo) { curRedo() }
                event.isCtrlPressed && event.key == Key.Y -> consumeIf(curCanRedo) { curRedo() }
                event.isCtrlPressed && event.key == Key.Z -> consumeIf(curCanUndo) { curUndo() }
                // Both spellings, and **consumed either way**: the editor owns the
                // keyboard while it is open, including for a press the open tool had no
                // shape selected to use it on.
                // The canvas's shape first; with none to take it, the timeline's clip (RD-301).
                event.key == Key.Delete || event.key == Key.Backspace ->
                    consume { if (curCanvasKeys?.onDelete?.invoke() != true) curTimelineKey(Key.Delete) }
                // **Consumed here, which is the whole point** (ME-207, found by hand):
                // this handler is previewed at the window root, before focus, and a rail
                // button keeps focus after it is clicked — so an unconsumed space
                // re-activated the last tool instead of pausing the video.
                event.key == Key.Spacebar -> consume { curTogglePlayback() }
                event.key == Key.DirectionLeft -> curStep(-1, !event.isAltPressed)
                event.key == Key.DirectionRight -> curStep(1, !event.isAltPressed)
                event.key == Key.F6 -> consume { curTogglePanel() }
                // `,` / `.` by the key, whatever the layout types on it (RD-501).
                !event.isCtrlPressed && event.layoutFreeKey() in TIMELINE_KEYS -> curTimelineKey(event.layoutFreeKey())
                else -> false
            }
        }
        onDispose {
            globalShortcuts.onPreviewKey = previous
            globalShortcuts.ownsWindow = previousOwnsWindow
        }
    }
}

/**
 * Split at the play head, and the play head to either end (RD-301, the prototype's `S`, `Home`,
 * `End`); one frame back or on (RD-501, `,` `.`) — a film's while paused, an animation's always.
 */
private val TIMELINE_KEYS = setOf(Key.S, Key.MoveHome, Key.MoveEnd, Key.Comma, Key.Period)

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
