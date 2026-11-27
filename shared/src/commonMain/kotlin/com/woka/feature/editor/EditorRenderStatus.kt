package com.woka.feature.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * What a render is doing, after the editor that started it is gone (ME-291, ME-293).
 *
 * `EditorSaveStatus`' shape, for `EditorSaveStatus`' reason — the editor closes first and
 * the work outlives it, so it belongs to an object and not to a composition — plus the two
 * things a save never needed (`render.md` §3.5): a **fraction**, because a render is
 * minutes and not milliseconds, and a **cancel**, because a render somebody regrets must
 * not have to be waited out.
 *
 * **One at a time** (`render.md` §4, "the honest first answer"). A second render while
 * one runs is refused at the door by [running] rather than queued: a queue is a list to
 * manage, and nobody asked for one.
 *
 * **Not counted in `EditorSaveStatus.inFlight`.** That gate holds the window open for a
 * grace period so a few-millisecond write can land; a render would only be killed at the
 * end of it anyway. The renderer cleans its own scratch file on JVM exit instead.
 */
@Stable
object EditorRenderStatus {

    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Null when nothing is running and nothing has just finished. */
    var message by mutableStateOf<String?>(null)
        private set

    /** 0..1 while running, null otherwise — the bar's fill. */
    var fraction by mutableStateOf<Float?>(null)
        private set

    /**
     * Where the last finished job wrote its file, for the toast's `Show in folder` (RD-401);
     * null while running and after anything else.
     */
    var savedTo by mutableStateOf<String?>(null)
        private set

    private var job: Job? = null

    val running: Boolean get() = job?.isActive == true

    /** [label] is the job in the prototype's words — "Creating copy", "Saving frame" — without the ellipsis. */
    fun started(label: String, work: Job) {
        job = work
        savedTo = null
        fraction = 0f
        message = "$label…"
    }

    fun progress(value: Float) {
        if (running) fraction = value.coerceIn(0f, 1f)
    }

    /** Every render lands beside its source (`renderTargetBeside`), which is what this says. */
    fun rendered(path: String) {
        fraction = null
        savedTo = path
        message = "Saved next to the original."
    }

    fun failed(reason: String) {
        fraction = null
        savedTo = null
        message = reason
    }

    /** The toast's `Cancel`: stops the work, and the renderer deletes what it wrote. */
    fun cancel() {
        job?.cancel()
        fraction = null
        savedTo = null
        message = "Cancelled. Nothing was saved."
    }

    fun clear() {
        message = null
        savedTo = null
    }
}
