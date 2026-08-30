package com.woka.feature.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** What a running save's message starts with, and therefore what does *not* settle. */
internal const val SAVING_PREFIX = "Saving"

/**
 * What the save is doing, after the editor that started it is gone (ME-130).
 *
 * **[scope] is the point of this object.** `Done` closes the editor first and writes
 * second, so a save launched in the composition's own scope would be cancelled by its
 * own success: `EditorHost` leaves the tree, `rememberCoroutineScope()` is cancelled,
 * and the write dies between the temp file and the atomic move. Nothing is
 * half-written — the move is the last step (§5.2) — but a `.tmp` is left beside the
 * photograph and the edit is quietly lost. So the work belongs to something that
 * outlives every composable, which is this. `Main` matches `EditorSession`:
 * `EditorEngine.save` moves itself to IO, and what stays here is the status write and
 * the `onSaved` callback, both of which touch Compose state.
 *
 * **This is not ME-125.** That question is what an in-flight save does when the
 * *application* closes, and a `SupervisorJob` in an object does not answer it.
 *
 * **One message, one save.** Two editors open at once is ME-128 and a list is what
 * that ticket brings; until then a second save replaces the first's message and
 * nothing worse, because these messages are the only shared state there is.
 */
@Stable
object EditorSaveStatus {

    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Null when nothing is running and nothing has just finished. */
    var message by mutableStateOf<String?>(null)
        private set

    /**
     * Named, never a bare "Saving…": the editor is off the screen by the time this is
     * read, so a message with no subject is a pill about nothing.
     */
    fun saving(name: String) {
        message = "$SAVING_PREFIX $name…"
    }

    fun saved(name: String) {
        message = "Saved $name"
    }

    /**
     * The same write, reported where its cause is not visible (ME-124).
     *
     * `Escape` in the video window returns to the film's folder and saves the editor on
     * the way through, so this pill is read by someone who is not in the editor and did
     * not press `Done`. "Saved photo.jpg" there is an answer to a question they never
     * asked; this one says why the file was written.
     */
    fun savedLeaving(name: String) {
        message = "Saved $name before returning to the video"
    }

    /**
     * The reason **as it is**. `ImageEditWrites` and `ImageSaveJob` already write
     * sentences aimed at the user — "The original is untouched." — and re-wording them
     * here would be a second vocabulary for the same failures.
     */
    fun failed(reason: String) {
        message = reason
    }

    fun clear() {
        message = null
    }
}
