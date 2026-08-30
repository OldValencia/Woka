package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable

/**
 * The three questions the editor asks before it throws something away: a revert, a
 * close with unsaved work, and — since ME-158 — a frame change with unsaved work.
 *
 * Split out of `EditorHost` when the third arrived. They are one concern: each is a
 * boolean the host holds, a dialog, and a confirmed action, and none of them touches
 * the session, the engine or the canvas.
 *
 * **At most one is ever open**, because each is raised by a control the others hide
 * behind, which is why a single [onKeepEditing] clearing all three is honest rather
 * than lax.
 */
@Composable
internal fun EditorHostDialogs(
    askingToRevert: Boolean,
    onRevert: () -> Unit,
    askingToDiscard: Boolean,
    onDiscard: () -> Unit,
    /** The frame the user reached for while the document was dirty (ME-158). */
    pendingFrame: Int?,
    onChangeFrame: () -> Unit,
    onKeepEditing: () -> Unit
) {
    if (askingToRevert) {
        RevertDialog(onRevert = onRevert, onKeepEditing = onKeepEditing)
    }

    if (askingToDiscard) {
        DiscardChangesDialog(onDiscard = onDiscard, onKeepEditing = onKeepEditing)
    }

    if (pendingFrame != null) {
        DiscardChangesDialog(
            onDiscard = onChangeFrame,
            onKeepEditing = onKeepEditing,
            body = CHANGING_FRAME
        )
    }
}
