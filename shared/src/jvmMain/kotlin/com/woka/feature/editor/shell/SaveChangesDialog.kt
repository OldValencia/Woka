package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import com.woka.feature.editor.tools.resize.SizeChoice

/**
 * What `Done` asks before it saves (RD-401, `#dialogs/done`) — a simpler Photoshop's save
 * options (the author, 2026-11-26).
 *
 * **A photo is asked its size**: [size] opens at what the document saves, and `Save` hands
 * back the choice — `Original` clears the Resize, a percentage saves a proportional one,
 * `Custom` the pixels. **A film is only asked to confirm** ([size] null): Resize sizes a
 * render, so its size is `Create a copy`'s question.
 */
@Composable
internal fun SaveChangesDialog(
    size: OutputSizeState?,
    onSave: (SizeChoice?) -> Unit,
    onCancel: () -> Unit
) {
    EditorDialogFrame("Save your changes?", "editor-save-dialog", onDismiss = onCancel, compact = true) {
        if (size == null) {
            EditorDialogText("Keep these edits so you can come back to them in Woka.", compact = true)
        } else {
            OutputSizeControls(size)
        }
        EditorDialogActions(compact = true) {
            EditorDialogButton("Cancel", "editor-save-cancel", DialogButtonKind.Quiet, onCancel, compact = true)
            EditorDialogButton(
                label = "Save",
                tag = "editor-save-confirm",
                kind = DialogButtonKind.Primary,
                onClick = { onSave(size?.choice) },
                enabled = size?.valid != false,
                compact = true
            )
        }
    }
}
