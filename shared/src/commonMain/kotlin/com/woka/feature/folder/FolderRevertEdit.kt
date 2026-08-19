package com.woka.feature.folder

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.dialogs.ConfirmationDialog
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.core.ui.theme.Theme
import java.io.File

/**
 * Revert without opening the editor (ME-107, step 5).
 *
 * The grid is where a photograph is *looked* at rather than worked on, and "put this
 * one back" is a thing to want there. Everything about what reverting means lives in
 * the engine; this is a menu entry, a question, and one call.
 */

/**
 * Whether [file] carries an edit — what greys the entry out.
 *
 * A few kilobytes off the file's head and free for a format with no carrier, which
 * answers from its extension. Asked once, when the menu opens, and not per frame.
 */
@Composable
internal fun rememberCarriesEdit(file: File): Boolean {
    var carries by remember(file.absolutePath) { mutableStateOf(false) }
    LaunchedEffect(file.absolutePath) { carries = AppliedStillEdits.carries(file) }
    return carries
}

/**
 * The same question the editor asks, in the same words.
 *
 * Not optional here either, and for a sharper reason than in the editor: this entry
 * sits in a menu with Delete, it takes effect on the file immediately, and there is no
 * open document to undo from afterwards.
 */
@Composable
internal fun RevertEditDialog(onRevert: () -> Unit, onKeepEdit: () -> Unit) {
    ConfirmationDialog(
        title = "Revert to original?",
        onConfirm = onRevert,
        onDismiss = onKeepEdit,
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

/**
 * The engine does the removing, exactly as it does for the editor's own control — the
 * eviction and the redraw signal come with it (ME-135), so the tile turns back on its
 * own. A file the factory refuses is a file with nothing to revert.
 */
internal suspend fun revertEdit(path: String) {
    EditorMenuDependencies.factory.create(path, 0)?.revert()
}
