package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.woka.feature.editor.EditorUndoToast
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.ownOpsOfOpenFrame
import java.io.File

/**
 * The things the editor does to a file — leave, write, revert, export — and the questions
 * asked before them (RD-401). Split out of `EditorHost` (ME-160): nothing here reads the
 * tools, the canvas or the registry. **A slot rather than a return value**, because this
 * emits the dialogs too.
 *
 * [session] is rebuilt per path *and* frame (ME-158), so `remember(session)` is the old
 * `remember(target.path, frameIndex)`.
 */
@Composable
internal fun EditorHostControls(
    engine: EditorEngine,
    session: EditorSession,
    file: File,
    editScope: EditScope,
    /** What the file carried when it opened — `!stored.isEmpty`, read once (ME-107). */
    carriesStoredEdit: Boolean,
    onFrameIndex: (Int) -> Unit,
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    /** Handed to the dialogs, which stand the editor's keys aside while they are up. */
    keys: CanvasKeys?,
    content: @Composable (EditorHostActions) -> Unit
) {
    // Whether the *file* still carries something to remove — seeded once, and moved by
    // the only thing in this session that can change it (ME-107).
    var carriesEdit by remember(session) { mutableStateOf(carriesStoredEdit) }
    var question by remember { mutableStateOf<EditorQuestion?>(null) }
    var frameAtMs by remember { mutableLongStateOf(0L) }

    val isDirty by session.isDirty.collectAsState()
    // Recomputed with the document, because that is what it is derived from.
    val document by session.document.collectAsState()
    val ownOps = remember(document) { session.ownOpsOfOpenFrame() }
    val scope = rememberCoroutineScope()
    // The Undo toast speaks for this session alone; it goes when the editor does.
    DisposableEffect(session) { onDispose { EditorUndoToast.dismiss() } }

    val leave = { if (isDirty) question = EditorQuestion.Keep else onClose() }

    // The write into the file that was opened, and the close. The order it closes in, and
    // the scope the write outlives this composable in, are `saveAction`'s (ME-130).
    val write = saveAction(engine, session, file.name, editScope, onClose, onSaved)
    // The window's close button saves as it was left — its own question, not `Done`'s.
    EditorExitEffect(isDirty = isDirty, onSave = write)

    val run = exportAction(engine, session, editScope, onClose, onSaved)
    val copyExport = engine.exports.firstOrNull { !it.frame }
    val frameExport = engine.exports.firstOrNull { it.frame }
    val choices = doneChoicesOf(
        engine = engine,
        onExport = { run(it, true) },
        onCopy = { if (copyExport != null) question = EditorQuestion.Copy },
        onSaveFrame = frameExport?.let {
            {
                frameAtMs = engine.playHeadMs() ?: 0L
                question = EditorQuestion.Frame
            }
        }
    )

    val revert = revertAction(
        engine = engine,
        session = session,
        scope = scope,
        editScope = { editScope },
        // The session's frame, not the door's: a film's open clip moves on the strip (ME-215).
        frameIndex = { session.frame }
    ) { reverted ->
        // Only an all-frames revert empties the file. A single-frame one takes a layer
        // off and leaves the base, so the file still carries something to remove.
        if (reverted && editScope == EditScope.AllFrames) carriesEdit = false
        question = null
    }

    content(
        EditorHostActions(
            leave = leave,
            // `Done` asks first (RD-401); a `RenderOnly` file's render takes its place.
            done = choices.instead ?: { question = EditorQuestion.Save },
            askRevert = { question = EditorQuestion.Revert },
            // **No question any more** (ME-169): the session keeps a document and a history
            // per frame, so walking away from one costs nothing.
            selectFrame = onFrameIndex,
            // Live while there is anything to take back: what the file carries, or what
            // this sitting has done and not saved. In Single Frame, this frame's own layer
            // and nothing else (ME-176, 2026-09-02).
            canRevert = when (editScope) {
                EditScope.AllFrames -> carriesEdit || isDirty
                EditScope.SingleFrame ->
                    session.frame in engine.frames.overridden || ownOps.isNotEmpty()
            },
            choices = choices
        )
    )

    EditorHostDialogs(
        question = question,
        engine = engine,
        session = session,
        revertScope = editScope,
        frameAtMs = frameAtMs,
        keys = keys,
        answers = EditorAnswers(
            revert = revert,
            keep = {
                question = null
                write()
            },
            discard = {
                question = null
                onClose()
            },
            save = { choice ->
                question = null
                choice?.let(session::saveSize)
                write()
            },
            copy = { size ->
                question = null
                copyExport?.let { run(it.copy(size = size), false) }
            },
            frame = {
                question = null
                frameExport?.let { run(it.copy(atMs = frameAtMs), false) }
            },
            dismiss = { question = null }
        )
    )
}
