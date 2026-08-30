package com.woka.feature.editor.shell

import com.woka.feature.editor.EditorSaveStatus
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * `Done`: close, then write (§4.5, ME-130).
 *
 * Built here rather than in `EditorHost` for two reasons. The host is at its size
 * limit, and — the one that matters — the order below is the whole ticket, so it is
 * worth a file that says so and a test that drives it without a window.
 *
 * Three things this has to get right, each a defect if it does not:
 *
 * - **The document is read before [onClose].** `onClose` clears the target, the exit
 *   animation runs, `EditorHost` disposes and `EditorSession.close()` cancels the
 *   session's scope. Reading `document.value` after that is reading a closed session.
 * - **[engine] is captured, not resolved.** It is a `remember`ed object holding a stamp
 *   and a lazy source size; the coroutine keeps it alive past the composition, which is
 *   exactly what is wanted — the save is still using it.
 * - **`NothingToDo` shows nothing** and clears any stale message. An empty document over
 *   a file that carries nothing is not an event, which `EditSaveResult` says itself.
 */
internal fun saveAction(
    engine: EditorEngine,
    session: EditorSession,
    name: String,
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    /**
     * Which finished message the pill gets. `Done` is read in the editor that produced
     * it; a return from the video window is read in the film's folder, where a bare
     * "Saved" has no visible cause (ME-124).
     */
    leavingForVideo: Boolean = false
): () -> Unit = {
    val document = session.document.value
    onClose()
    EditorSaveStatus.saving(name)
    EditorSaveStatus.scope.launch {
        when (val result = engine.save(document)) {
            is EditSaveResult.Saved -> {
                if (leavingForVideo) EditorSaveStatus.savedLeaving(name)
                else EditorSaveStatus.saved(name)
                onSaved(result.path)
            }
            is EditSaveResult.Failed -> EditorSaveStatus.failed(result.reason)
            EditSaveResult.NothingToDo -> EditorSaveStatus.clear()
        }
    }
    Unit
}

/**
 * What the video window invokes on its way back to the main one (ME-124).
 *
 * **The dirty check is here, not at the call site.** `PipManager` asks the editor to
 * leave and is told nothing about it; whether leaving means writing is the editor's to
 * answer. A file opened and not touched still has a document — the recipe it already
 * carried (ME-134) — so an unconditional save would rewrite it, answer `Saved`, and put
 * up a pill about an edit that never happened.
 *
 * `session.isDirty.value` is read **when the action runs**, so a session that went dirty
 * after this was published is saved and one that was cleaned is not.
 */
internal fun leaveForVideoAction(
    engine: EditorEngine,
    session: EditorSession,
    name: String,
    onClose: () -> Unit,
    onSaved: (String) -> Unit
): () -> Unit {
    val save = saveAction(engine, session, name, onClose, onSaved, leavingForVideo = true)
    return { if (session.isDirty.value) save() else onClose() }
}

/**
 * Revert: the file first, the session second (ME-107).
 *
 * The order is the rule — the engine owns the file and the session only mirrors it — and
 * a failed write moves neither, so the editor never claims a revert the disk refused.
 * [onFinished] receives whether it happened, because what the *shell* does with that is
 * its own state: a mark to clear and a dialog to dismiss, and the dialog closes either
 * way.
 *
 * Unlike `saveAction` this one runs in the caller's scope: a revert has no reason to
 * outlive the editor, and the editor is still standing when it ends.
 */
internal fun revertAction(
    engine: EditorEngine,
    session: EditorSession,
    scope: CoroutineScope,
    onFinished: (reverted: Boolean) -> Unit
): () -> Unit = {
    scope.launch {
        val reverted = engine.revert() !is EditSaveResult.Failed
        if (reverted) session.revertedToOriginal()
        onFinished(reverted)
    }
    Unit
}
