package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.woka.feature.editor.tools.hdr.bakesToSdr
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.ownOpsOfOpenFrame
import java.io.File

/** What the editor's controls invoke, and whether Revert has anything to take back. */
@Stable
internal class EditorHostActions(
    val leave: () -> Unit,
    val done: () -> Unit,
    val askRevert: () -> Unit,
    val selectFrame: (Int) -> Unit,
    val canRevert: Boolean
)

/**
 * The three things the editor does to a file — leave, write, revert — and the four
 * questions asked before them.
 *
 * Split out of `EditorHost` when ME-160's one call line carried that file past 150.
 * The seam is the same one `EditorPresenceEffect` and `EditorHostDialogs` were cut
 * along: nothing here reads the tools, the canvas or the registry, and everything the
 * host kept is about *building* the editor rather than acting on it.
 *
 * **A slot rather than a return value**, because this emits: the dialogs and the four
 * booleans behind them are one concern, and handing the booleans back to the host so
 * it could draw them would put the whole of what moved back where it came from.
 *
 * Keys are the ones the host used, restated in terms of what it passes: [session] is
 * rebuilt per path *and* frame (ME-158), so `remember(session)` is the old
 * `remember(target.path, frameIndex)`; [file] is rebuilt per path alone, which is what
 * `pendingFrame` was keyed on.
 */
@Composable
internal fun EditorHostControls(
    engine: EditorEngine,
    session: EditorSession,
    file: File,
    editScope: EditScope,
    /** Which frame the switch's Single Frame means, for Revert (ME-176). */
    frameIndex: Int,
    /** What the file carried when it opened — `!stored.isEmpty`, read once (ME-107). */
    carriesStoredEdit: Boolean,
    onFrameIndex: (Int) -> Unit,
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    content: @Composable (EditorHostActions) -> Unit
) {
    // Whether the *file* still carries something to remove — seeded once, and moved by
    // the only thing in this session that can change it (ME-107).
    var carriesEdit by remember(session) { mutableStateOf(carriesStoredEdit) }
    var askingToRevert by remember { mutableStateOf(false) }
    var askingToDiscard by remember { mutableStateOf(false) }
    var askingToBake by remember { mutableStateOf(false) }

    val isDirty by session.isDirty.collectAsState()
    // Recomputed with the document, because that is what it is derived from.
    val document by session.document.collectAsState()
    val ownOps = remember(document) { session.ownOpsOfOpenFrame() }
    val scope = rememberCoroutineScope()

    val leave = { if (isDirty) askingToDiscard = true else onClose() }

    // `Done` writes into the file that was opened and closes. **There is nothing to
    // choose** (§4.5) beyond which frames it is for. The order it closes in, and the
    // scope the write outlives this composable in, are `saveAction`'s (ME-130).
    val write = saveAction(engine, session, file.name, editScope, onClose, onSaved)
    // **The one question `Done` asks** (ME-118). Everything else it writes is a recipe
    // the file can give back; `To SDR` rewrites the pixels, so it is asked about before
    // it happens rather than regretted after.
    val done = { if (bakesToSdr(document)) askingToBake = true else write() }
    EditorExitEffect(isDirty = isDirty, onSave = done)

    val revert = revertAction(
        engine = engine,
        session = session,
        scope = scope,
        editScope = { editScope },
        frameIndex = { frameIndex }
    ) { reverted ->
        // Only an all-frames revert empties the file. A single-frame one takes a layer
        // off and leaves the base, so the file still carries something to remove.
        if (reverted && editScope == EditScope.AllFrames) carriesEdit = false
        askingToRevert = false
    }

    content(
        EditorHostActions(
            leave = leave,
            done = done,
            askRevert = { askingToRevert = true },
            // **No question any more** (ME-169). ME-158 asked because the host rebuilt
            // the session on a frame change and the work went with it; the session now
            // keeps a document and a history per frame, so walking away from one costs
            // nothing and coming back finds it as it was left.
            selectFrame = onFrameIndex,
            // Live while there is anything to take back: what the file carries, or what
            // this sitting has done and not saved. Both end at the same picture.
            //
            // **In Single Frame it asks a narrower question** (ME-176): this frame's own
            // layer, which only exists where the file carries a key for it. A control
            // that offered to remove nothing would be a control that does nothing.
            canRevert = when (editScope) {
                EditScope.AllFrames -> carriesEdit || isDirty
                // **This frame's own layer, and nothing else** (2026-09-02). Asking the
                // file alone greyed the button over unsaved work; asking `isDirty` lit it
                // over work that belongs to the *shared* layer, and pressing it then did
                // nothing visible — a Single Frame revert removes a frame's own half.
                EditScope.SingleFrame ->
                    frameIndex in engine.frames.overridden || ownOps.isNotEmpty()
            }
        )
    )

    EditorHostDialogs(
        askingToRevert = askingToRevert,
        revertScope = editScope,
        onRevert = revert,
        askingToDiscard = askingToDiscard,
        onDiscard = { askingToDiscard = false; onClose() },
        askingToBake = askingToBake,
        onBake = { askingToBake = false; write() },
        onKeepEditing = {
            askingToRevert = false
            askingToDiscard = false
            askingToBake = false
        }
    )
}
