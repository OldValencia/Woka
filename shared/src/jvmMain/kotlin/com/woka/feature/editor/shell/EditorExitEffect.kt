package com.woka.feature.editor.shell

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
import com.woka.app.shell.AppExitRequest
import com.woka.app.shell.ExitVerdict
import com.woka.core.ui.dialogs.ChoiceDialog
import com.woka.core.ui.theme.Theme

/**
 * The editor's answer to "the application is closing" (ME-160).
 *
 * **Why the editor answers and the door does not ask itself.** Dirtiness is not
 * readable from `app/shell`: a file opened and never touched still has a document — the
 * recipe it already carried (ME-134) — which is the same reason `EditorPresence`
 * publishes a lambda rather than an `isDirty` (ME-124). So `AppExitRequest` carries the
 * question and this carries the answer.
 *
 * **`Save` runs [onSave] *before* it answers.** [onSave] is `saveAction`, which raises
 * `EditorSaveStatus.started()` synchronously, so the count is already 1 when
 * `ExitWhenQuiet` resumes and its existing wait holds the window until the write lands.
 * Answering first would race the gate against a save that had not begun.
 *
 * **`saveAction` and not `EditorPresence.saveAndClose`**: the latter is
 * `leaveForVideoAction`, whose pill reads "Saved photo.jpg before returning to the
 * video" — a sentence about a video window nobody is going back to.
 *
 * **It owns its own state and draws its own dialog**, unlike the other three questions,
 * which `EditorHostDialogs` holds together. `EditorHost` is at its line ceiling and the
 * alternative was splitting it for one call. The cost is that this dialog and one of
 * those three can be on screen at once — reachable only by pressing the window's close
 * button with a confirmation already up, and the exit question is the one that should be
 * answered first anyway.
 */
@Composable
internal fun EditorExitEffect(isDirty: Boolean, onSave: () -> Unit) {
    var asking by remember { mutableStateOf(false) }

    // Keyed on the request alone. The question is asked once per press, and re-running
    // it because the document changed underneath would re-answer one already answered.
    LaunchedEffect(AppExitRequest.requested) {
        if (!AppExitRequest.requested) {
            asking = false
            return@LaunchedEffect
        }
        if (isDirty) asking = true
        else AppExitRequest.answer(ExitVerdict.Leave)
    }

    if (asking) {
        ChoiceDialog(
            title = "Save before quitting?",
            onSave = {
                asking = false
                onSave()
                AppExitRequest.answer(ExitVerdict.Save)
            },
            onDiscard = {
                asking = false
                AppExitRequest.answer(ExitVerdict.Leave)
            },
            onCancel = {
                asking = false
                AppExitRequest.cancel()
            },
            width = 360.dp
        ) {
            BasicText(
                text = "This edit has not been saved — the original file is untouched " +
                    "either way.",
                style = TextStyle(color = Theme.colors.textSecondary, fontSize = 14.sp)
            )
        }
    }
}
