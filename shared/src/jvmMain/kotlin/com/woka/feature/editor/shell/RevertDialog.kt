package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.feature.editor.core.EditScope

/**
 * Asked before Revert, and **not optional** (ME-107), in the prototype's frame since RD-401
 * (`#dialogs/revert`): what goes, that it cannot come back, and two full-width buttons.
 *
 * **"This cannot be undone."** (the author, 2026-11-26): the prototype's "You can undo this
 * action" was wrong for the app — Revert removes the recipe from the file, and the session
 * starts again from what the file now holds.
 */
@Composable
internal fun RevertDialog(
    onRevert: () -> Unit,
    onKeepEditing: () -> Unit,
    /**
     * Which Revert this is (ME-176). The two destroy different amounts, so they must not
     * say the same thing.
     */
    scope: EditScope = EditScope.AllFrames,
    /** A film's revert takes its cuts too, and says so. */
    video: Boolean = false
) {
    val single = scope == EditScope.SingleFrame
    val whole = if (video) "the video, including cuts," else "the photo"
    EditorDialogFrame(
        title = if (single) "Revert this frame?" else "Revert to original?",
        tag = "editor-revert-dialog",
        onDismiss = onKeepEditing
    ) {
        EditorDialogText(if (single) SINGLE_BODY else "All edits to $whole will be reset. This cannot be undone.")
        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            EditorDialogButton(
                label = if (single) "Revert frame" else "Revert to original",
                tag = "editor-revert-confirm",
                kind = DialogButtonKind.Primary,
                onClick = onRevert,
                modifier = Modifier.fillMaxWidth().padding(top = EditorDialogTokens.Button.wideTop)
            )
            EditorDialogButton(
                label = "Cancel",
                tag = "editor-revert-cancel",
                kind = DialogButtonKind.Quiet,
                onClick = onKeepEditing,
                modifier = Modifier.fillMaxWidth().padding(top = EditorDialogTokens.Button.wideTop)
            )
        }
    }
}

/**
 * **Not "as shot", and that distinction is the whole reason this string exists.**
 *
 * A frame reverted on its own goes back to what the *rest of the file* draws, which is
 * the file's own edit still applying to it (ME-175). Telling somebody their picture
 * returns to the camera's version, and then showing them a warmed frame, would be a
 * dialog that lied about what its button did.
 */
private const val SINGLE_BODY =
    "This frame stops carrying an edit of its own. It goes back to drawing whatever " +
        "the rest of the file draws — which is not the original if the file itself " +
        "carries an edit. Every other frame is untouched. This cannot be undone."
