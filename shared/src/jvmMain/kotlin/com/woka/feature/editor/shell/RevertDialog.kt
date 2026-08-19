package com.woka.feature.editor.shell

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.dialogs.ConfirmationDialog
import com.woka.core.ui.theme.Theme

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
    onKeepEditing: () -> Unit
) {
    ConfirmationDialog(
        title = "Revert to original?",
        onConfirm = onRevert,
        onDismiss = onKeepEditing,
        width = 340.dp,
        confirmText = "Revert",
        dismissText = "Keep the edit"
    ) {
        BasicText(
            text = "Every edit on this photograph is removed and the file goes back to " +
                "exactly what came off the camera. The pixels were never changed — what " +
                "goes is the record of the edit, and it cannot be undone.",
            style = TextStyle(color = Theme.colors.textSecondary, fontSize = 14.sp)
        )
    }
}
