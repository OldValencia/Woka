package com.woka.core.ui.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent

/**
 * Right-click a control, and the value it holds goes back to its default — where an
 * editor tool declared it (asked for 2026-08-29), and since 2026-11-01 every slider in
 * the app that has a resting value: playback speed to 1×, volume to 100 %, zoom to Fit.
 * Moved here from the editor's shell when the viewer's sliders took it up.
 *
 * **One gesture for every control**, which is the only thing that makes it worth
 * learning: a colour, a level, a set of faces and a plate all answer it, so nothing has
 * to be remembered per row. It replaces nothing — `Reset` still sits in the value
 * popover for anyone who never right-clicks — and it costs no pixels, which is what a
 * panel drawn over a photograph is short of.
 */

/**
 * The press, taken on the way **down** rather than on release.
 *
 * `PointerEventType.Press` with the secondary button is the whole gesture; a
 * `detectTapGestures(onLongPress = …)`-style wait would put a delay on something that is
 * unambiguous the moment the button goes down. Consumed, so a right-click on a control
 * inside a popover does not also reach whatever is behind it.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.resetOnRightClick(onReset: () -> Unit): Modifier =
    onPointerEvent(PointerEventType.Press) { event ->
        if (event.button == PointerButton.Secondary) {
            val change = event.changes.first()
            if (!change.isConsumed) {
                change.consume()
                onReset()
            }
        }
    }
