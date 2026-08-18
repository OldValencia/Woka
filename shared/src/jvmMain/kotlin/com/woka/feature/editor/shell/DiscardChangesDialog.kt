package com.woka.feature.editor.shell

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.dialogs.ConfirmationDialog
import com.woka.core.ui.theme.Theme

/**
 * Asked when `Cancel` or `Esc` arrives with unsaved work.
 *
 * **Not over media**, so it takes `Theme.colors` rather than the `onScrim` set — it is
 * an app surface that happens to appear above one, the same as the overwrite
 * confirmation ME-130 adds beside it.
 *
 * The affirmative button is the *destructive* one, which is why it says `Discard`
 * rather than `Yes`: the question is what happens to the user's work, and a `Yes/No`
 * pair makes the reader reconstruct which is which.
 */
@Composable
internal fun DiscardChangesDialog(
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit
) {
    ConfirmationDialog(
        title = "Discard changes?",
        onConfirm = onDiscard,
        onDismiss = onKeepEditing,
        width = 340.dp,
        confirmText = "Discard",
        dismissText = "Keep editing"
    ) {
        BasicText(
            text = "This edit has not been saved. Closing the editor now loses it — " +
                "the original file is untouched either way.",
            style = TextStyle(color = Theme.colors.textSecondary, fontSize = 14.sp)
        )
    }
}
