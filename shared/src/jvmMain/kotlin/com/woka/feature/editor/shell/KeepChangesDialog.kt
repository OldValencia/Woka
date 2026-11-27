package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable

/**
 * Asked when the editor is left with unsaved work (RD-401, `#dialogs/keep`, `experience.js`
 * `requestExit`) — `DiscardChangesDialog` until then.
 *
 * **Three answers, not two.** The old dialog only offered to throw the work away; the
 * prototype offers to keep it on the way out as well, which is what someone pressing
 * `Cancel` after an hour's work most often meant. `Keep changes` is `Done` without its size
 * question: the size stays as it was saved.
 */
@Composable
internal fun KeepChangesDialog(
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit,
    onKeep: () -> Unit
) {
    EditorDialogFrame("Keep your changes?", "editor-keep-dialog", onDismiss = onKeepEditing) {
        EditorDialogText("You can come back and edit again at any time.")
        EditorDialogActions {
            EditorDialogButton("Keep editing", "editor-keep-editing", DialogButtonKind.Secondary, onKeepEditing)
            EditorDialogButton("Discard changes", "editor-keep-discard", DialogButtonKind.Quiet, onDiscard)
            EditorDialogButton("Keep changes", "editor-keep-keep", DialogButtonKind.Primary, onKeep)
        }
    }
}
