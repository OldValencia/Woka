package com.woka.feature.editor

import androidx.compose.runtime.Composable
import com.woka.feature.editor.shell.EditorHost

/**
 * The editor over the viewer, or nothing.
 *
 * **No slide in or out** (the author, 2026-11-25). The editor used to enter from the right
 * edge and leave through it; since RD-201 the picture stays where the viewer had it and the
 * room and the inspector arrive around it, so a whole-screen slide on top of that was a
 * second movement fighting the first.
 */
@Composable
actual fun NativeMediaEditor(
    target: EditorTarget?,
    onClose: () -> Unit,
    onSaved: (String) -> Unit
) {
    if (target != null) EditorHost(target = target, onClose = onClose, onSaved = onSaved)
}
