package com.woka.feature.editor.shell

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.dialogs.ConfirmationDialog
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditScope

/**
 * Asked before Revert, and **not optional** (ME-107).
 *
 * The control sits beside Back, which is where the hand goes to leave, so a misclick
 * there is a question of when rather than whether. Reverting does not endanger the
 * photograph — the pixels have never been touched — but an afternoon of work is gone,
 * and that deserves one question.
 *
 * `ConfirmationDialog` rather than a second dialog of its shape, for the reason
 * `DiscardChangesDialog` beside it gives: two buttons and different words is not a new
 * component. The affirmative is the destructive one, so it says what it does.
 */
@Composable
internal fun RevertDialog(
    onRevert: () -> Unit,
    onKeepEditing: () -> Unit,
    /**
     * Which Revert this is (ME-176). The two destroy different amounts, so they must not
     * say the same thing: "this cannot be undone" is true of both and useful for
     * neither.
     */
    scope: EditScope = EditScope.AllFrames
) {
    val single = scope == EditScope.SingleFrame
    ConfirmationDialog(
        title = if (single) "Revert this frame?" else "Revert to original?",
        onConfirm = onRevert,
        onDismiss = onKeepEditing,
        width = 340.dp,
        confirmText = if (single) "Revert frame" else "Revert",
        dismissText = "Keep the edit"
    ) {
        BasicText(
            text = if (single) SINGLE_BODY else "Every edit on this photograph is removed and the file goes back to " +
                "exactly what came off the camera. The pixels were never changed — what " +
                "goes is the record of the edit, and it cannot be undone.",
            style = TextStyle(color = Theme.colors.textSecondary, fontSize = 14.sp)
        )
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
        "carries an edit. Every other frame is untouched."
