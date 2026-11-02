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
 * An entry of `Done ▾` other than `Done`: close, then render (ME-291).
 *
 * `saveAction`'s order for `saveAction`'s reasons — the layers are read before [onClose],
 * because closing cancels the session, and the work runs in an object's scope because it
 * outlives the editor. Two differences, both from `render.md` §3.1 and §3.5:
 *
 * - **The source is not written.** A render is a new file beside it and the edit lives in
 *   that file; iOS Photos' *Save as New Clip* leaves the original as shot, and so does
 *   this. Whoever wants the recipe in the source as well presses `Done`.
 * - **A second render while one runs is refused, and the editor stays open**, so the work
 *   is not thrown away by a click that could not be honoured.
 *
 * The job is started `LAZY` so the status holds it **before** it runs: a cancel pressed
 * in the first frame must find something to cancel.
 */
internal fun exportAction(
    engine: EditorEngine,
    session: EditorSession,
    file: File,
    scope: EditScope,
    onClose: () -> Unit,
    onSaved: (String) -> Unit
): (EditorExport) -> Unit = { export ->
    if (EditorRenderStatus.running) {
        EditorRenderStatus.failed("A render is already running. Wait for it, or cancel it first.")
    } else {
        val layers = session.layers(scope)
        onClose()
        val job = EditorRenderStatus.scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = engine.export(export, layers) { fraction ->
                    EditorRenderStatus.scope.launch { EditorRenderStatus.progress(fraction) }
                }
                when (result) {
                    is EditSaveResult.Saved -> {
                        EditorRenderStatus.rendered(File(result.path).name)
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
        EditorRenderStatus.started(file.name, job)
        job.start()
    }
}
