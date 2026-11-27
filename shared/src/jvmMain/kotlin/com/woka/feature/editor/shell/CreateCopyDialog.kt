package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.woka.feature.viewer.shell.ViewerTooltip

/**
 * `Create a finished copy` (RD-401, `#dialogs/copy`): a new file with the edits applied, at the
 * chosen size, beside the original — which stays as it is.
 *
 * **No SDR switch** (the author, 2026-11-27): the copy is what the screen shows, under the
 * pill's colour mode. [retone] is the mode's name when it changes the picture — `SDR` on an
 * HDR file, `HDR` on an SDR one — and the dialog says so, since the copy's tone mapping
 * changes with it. `Audio only…` is drawn and disabled until ME-245 (1.1.0).
 */
@Composable
internal fun CreateCopyDialog(
    size: OutputSizeState,
    video: Boolean,
    retone: String?,
    onCreate: (IntSize?) -> Unit,
    onCancel: () -> Unit
) {
    EditorDialogFrame("Create a finished copy", "editor-copy-dialog", onDismiss = onCancel, compact = true) {
        EditorDialogText("A new file with your edits. The original stays as it is.", compact = true)
        OutputSizeControls(size)
        if (retone != null) {
            EditorDialogText(
                "Colour is set to $retone on screen. The copy follows it, so its tone mapping changes too.",
                compact = true,
                modifier = Modifier.padding(top = 10.dp).testTag("editor-copy-retone")
            )
        }
        EditorDialogActions(compact = true, leading = if (video) { { AudioOnly() } } else null) {
            EditorDialogButton("Cancel", "editor-copy-cancel", DialogButtonKind.Quiet, onCancel, compact = true)
            EditorDialogButton(
                label = "Create copy",
                tag = "editor-copy-confirm",
                kind = DialogButtonKind.Primary,
                onClick = { onCreate(size.exportSize) },
                enabled = size.valid,
                compact = true
            )
        }
    }
}

/** ME-245's door, drawn so the dialog's shape is final, and closed until it ships. */
@Composable
private fun AudioOnly() {
    ViewerTooltip("Coming in 1.1.0") {
        EditorDialogButton("Audio only…", "editor-copy-audio", DialogButtonKind.Quiet, {}, enabled = false, compact = true)
    }
}
