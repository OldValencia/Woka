package com.woka.app.shell

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What an open editor answered when the application asked to close (ME-160). */
enum class ExitVerdict {

    /**
     * Write the edit first. The editor starts the save *before* it answers, so
     * `EditorSaveStatus.inFlight` is already 1 by the time [ExitWhenQuiet] resumes and
     * the wait ME-125 built is the whole of this branch.
     */
    Save,

    /** Go now: the document was clean, or the user chose to lose it. */
    Leave
}

/**
 * The handshake between the exit doors and the editor (ME-160).
 *
 * **Why the door does not decide.** `ExitWhenQuiet` may not learn whether a document is
 * dirty. `EditorPresence`'s KDoc says why for ME-124's case and it holds here: a file
 * opened and never touched still has a document — the recipe it already carried
 * (ME-134) — so "is there anything to lose" is the editor's answer to give. The door
 * [request]s, the editor [answer]s.
 *
 * **Its own object rather than two more fields on `EditorPresence`.** That one is about
 * an editor *existing* and what to invoke on the way out of the video window; an exit
 * state machine parked beside it would make one object two.
 *
 * **[cancel] clears [requested], and that is the whole reason this is state.** A flag
 * left true is a `LaunchedEffect(requested)` that will not restart, so the second press
 * of the close button after a `Cancel` would do nothing at all — the window would
 * simply stop closing, with no way to find out why.
 */
@Stable
object AppExitRequest {

    var requested by mutableStateOf(false)
        private set

    /** Null while the question is unanswered — which is what the gate waits on. */
    var verdict by mutableStateOf<ExitVerdict?>(null)
        private set

    fun request() {
        requested = true
    }

    fun answer(verdict: ExitVerdict) {
        this.verdict = verdict
    }

    fun cancel() {
        requested = false
        verdict = null
    }
}
