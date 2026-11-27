package com.woka.feature.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.woka.feature.editor.shell.EditorHost

/**
 * The editor over the viewer, or nothing.
 *
 * **No slide in or out** (the author, 2026-11-25). The editor used to enter from the right
 * edge and leave through it; since RD-201 the picture stays where the viewer had it and the
 * room and the inspector arrive around it, so a whole-screen slide on top of that was a
 * second movement fighting the first.
 *
 * **Nothing the pointer does reaches the viewer underneath** (found by hand, 2026-11-27).
 * The viewer stays composed below, and Compose hands an event to the sibling beneath
 * wherever the one above has no handler: a click in the hairline under the inspector's tabs
 * played the viewer's film. A pointer handler over the whole editor makes it the hit target
 * everywhere, so the viewer is never asked.
 */
@Composable
actual fun NativeMediaEditor(
    target: EditorTarget?,
    onClose: () -> Unit,
    onSaved: (String) -> Unit
) {
    if (target == null) return
    Box(Modifier.fillMaxSize().swallowPointer()) {
        EditorHost(target = target, onClose = onClose, onSaved = onSaved)
    }
}

/**
 * A hit target over the whole node — clicks, hover, the wheel — that **consumes nothing**.
 * Being hit is what keeps the sibling beneath out; consuming here would also cancel the
 * editor's own taps, which check for consumption on the final pass a parent reaches first.
 */
internal fun Modifier.swallowPointer(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Final)
    }
}
