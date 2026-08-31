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
 * **Closing the window waits for it** (ME-125). What a `SupervisorJob` in an object
 * outlives is every composable, not the process — so [inFlight] is the fact the two
 * exit doors read: a close request is deferred while a save is running, and taken
 * anyway once the grace period is up. The gate itself is `app/shell/AppExit.kt`,
 * because the thing being closed is the application and not an editor.
 *
 * **One message, one save.** Two editors open at once is ME-128 and a list is what
 * that ticket brings; until then a second save replaces the first's message and
 * nothing worse, because these messages are the only shared state there is.
 */
@Stable
object EditorSaveStatus {

    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * How many saves are running, and therefore whether the application may go
     * (ME-125). `ExitWhenQuiet` reads it through a `snapshotFlow`.
     *
     * **A count rather than a flag**, for the reason `EditorPresence` keeps one:
     * ME-128 makes two saves at once reachable, and a boolean would clear on the
     * first of them to finish and let the window close over the second.
     */
    var inFlight by mutableStateOf(0)
        private set

    fun started() {
        inFlight++
    }

    /**
     * `coerceAtLeast` because a count that goes negative wedges the gate open for
     * the rest of the session: nothing afterwards can bring it back to zero, and
     * every close request then waits out the full grace period.
     */
    fun finished() {
        inFlight = (inFlight - 1).coerceAtLeast(0)
    }

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
