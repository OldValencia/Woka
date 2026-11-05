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

    private var job: Job? = null

    val running: Boolean get() = job?.isActive == true

    fun started(name: String, work: Job) {
        job = work
        fraction = 0f
        message = "Rendering $name"
    }

    fun progress(value: Float) {
        if (running) fraction = value.coerceIn(0f, 1f)
    }

    fun rendered(name: String) {
        fraction = null
        message = "Rendered $name"
    }

    fun failed(reason: String) {
        fraction = null
        message = reason
    }

    /** The ✕ on the banner: stops the work, and the renderer deletes what it wrote. */
    fun cancel() {
        job?.cancel()
        fraction = null
        message = "Render cancelled. Nothing was written."
    }

    fun clear() {
        message = null
    }
}
