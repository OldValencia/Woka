package com.woka.feature.editor.shell

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.dialogs.ConfirmationDialog
import com.woka.core.ui.theme.Theme

/**
 * What leaving unsaved work behind costs, named rather than described in general.
 *
 * The default is `Cancel` and `Esc`; ME-158 asks the same question when the *frame*
 * changes, which rebuilds the engine, the document and the session and would discard
 * the work just as completely. One component, one pair of buttons, one sentence apart
 * — a second dialog for the second cause would be two things to keep in step.
 */
private const val LEAVING = "This edit has not been saved. Closing the editor now loses it"

/** ME-158: the same loss, reached from inside the editor rather than out of it. */
internal const val CHANGING_FRAME =
    "This edit has not been saved. Moving to another frame now loses it"

/**
 * Asked when unsaved work is about to be thrown away.
 *
 * **Not over media**, so it takes `Theme.colors` rather than the `onScrim` set — it is
 * an app surface that happens to appear above one.
 *
 * The affirmative button is the *destructive* one, which is why it says `Discard`
 * rather than `Yes`: the question is what happens to the user's work, and a `Yes/No`
 * pair makes the reader reconstruct which is which.
 */
@Composable
internal fun DiscardChangesDialog(
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
    /** What is being left. Defaulted to closing, which is where this dialog began. */
    body: String = LEAVING
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
            text = "$body — the original file is untouched either way.",
            style = TextStyle(color = Theme.colors.textSecondary, fontSize = 14.sp)
        )
    }
}
