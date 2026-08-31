package com.woka.core.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.core.ui.theme.Theme

/**
 * A question with three answers (ME-160).
 *
 * **Why not `ConfirmationDialog`.** Its pair is right while the editor stays on screen:
 * `DiscardChangesDialog` asks `Discard` / `Keep editing`, and "keep" there means
 * *carry on editing*. Quitting has a third answer — write it and then go — and folding
 * that into "keep editing" makes the user cancel, press `Done`, watch for a pill and
 * press close a second time.
 *
 * **Its own file rather than a fifth function in `Dialogs.kt`.** That file is at 141
 * lines and a third button carries it past 150. Nothing here is new chrome:
 * [AbstractDialog], [DialogTitle] and [DialogButton] are the same three this app's
 * other four dialogs are built from, in the same order and at the same spacing.
 *
 * The colours say what each answer costs — the destructive one in `danger`, the
 * affirmative one on the accent, the retreat in `textSecondary` — which is
 * `ConfirmationDialog`'s rule with one more button under it. Dismissing (`Esc`, a click
 * outside) is [onCancel], because the answer that changes nothing is the safe default.
 */
@Composable
fun ChoiceDialog(
    title: String,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
    width: Dp = 300.dp,
    saveText: String = "Save",
    discardText: String = "Don't save",
    cancelText: String = "Cancel",
    content: @Composable ColumnScope.() -> Unit
) {
    AbstractDialog(onDismissRequest = onCancel, width = width) {
        DialogTitle(text = title, color = Theme.colors.textPrimary)
        content()
        Row(
            modifier = Modifier.align(Alignment.End),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DialogButton(text = cancelText, onClick = onCancel, color = Theme.colors.textSecondary)
            DialogButton(text = discardText, onClick = onDiscard, color = Theme.colors.danger)
            DialogButton(text = saveText, onClick = onSave, color = Theme.colors.accent)
        }
    }
}
