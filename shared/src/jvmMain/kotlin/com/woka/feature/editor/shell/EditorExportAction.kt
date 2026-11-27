package com.woka.feature.editor.shell

import com.woka.feature.editor.EditorRenderStatus
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorExport
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.layers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import java.io.File

/**
 * An export — `Create a copy`, `Save frame as photo`, a `RenderOnly` file's `Done` — run in
 * the background with the progress toast (ME-291, RD-401).
 *
 * `saveAction`'s order for `saveAction`'s reasons — the layers are read first, and the work
 * runs in an object's scope because it may outlive the editor. **Whether the editor closes is
 * the caller's** (`closing`): a `RenderOnly` file's `Done` closes as `Done` does; a copy or a
 * frame leaves the editor open, as the prototype does, and the toast reports over it.
 *
 * - **The source is not written.** A render is a new file beside it and the edit lives in
 *   that file; whoever wants the recipe in the source as well presses `Done`.
 * - **A second render while one runs is refused, and the editor stays open**, so the work
 *   is not thrown away by a click that could not be honoured.
 *
 * The job is started `LAZY` so the status holds it **before** it runs: a cancel pressed
 * in the first frame must find something to cancel.
 */
internal fun exportAction(
    engine: EditorEngine,
    session: EditorSession,
    scope: EditScope,
    onClose: () -> Unit,
    onSaved: (String) -> Unit
): (EditorExport, Boolean) -> Unit = { export, closing ->
    if (EditorRenderStatus.running) {
        EditorRenderStatus.failed("A render is already running. Wait for it, or cancel it first.")
    } else {
        val layers = session.layers(scope)
        // Read before a close takes the player with it (ME-294); a frame's dialog stamped its own.
        val chosen = if (export.atMs != null) export else export.copy(atMs = engine.playHeadMs())
        if (closing) onClose()
        val job = EditorRenderStatus.scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = engine.export(chosen, layers) { fraction ->
                    EditorRenderStatus.scope.launch { EditorRenderStatus.progress(fraction) }
                }
                when (result) {
                    is EditSaveResult.Saved -> {
                        EditorRenderStatus.rendered(File(result.path).absolutePath)
                        onSaved(result.path)
                    }
                    is EditSaveResult.Failed -> EditorRenderStatus.failed(result.reason)
                    EditSaveResult.NothingToDo -> EditorRenderStatus.clear()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                EditorRenderStatus.failed("The render failed: ${failure.message}. The original is untouched.")
            }
        }
        EditorRenderStatus.started(chosen.progress, job)
        job.start()
    }
}
