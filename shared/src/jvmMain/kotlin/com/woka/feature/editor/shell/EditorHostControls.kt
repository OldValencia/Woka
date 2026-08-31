package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession
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
    var pendingFrame by remember(file) { mutableStateOf<Int?>(null) }

    val isDirty by session.isDirty.collectAsState()
    val scope = rememberCoroutineScope()

    val leave = { if (isDirty) askingToDiscard = true else onClose() }

    // `Done` writes into the file that was opened and closes. **There is nothing to
    // choose** (§4.5) beyond which frames it is for. The order it closes in, and the
    // scope the write outlives this composable in, are `saveAction`'s (ME-130).
    val done = saveAction(engine, session, file.name, editScope, onClose, onSaved)
    EditorExitEffect(isDirty = isDirty, onSave = done)

    val revert = revertAction(engine, session, scope) { reverted ->
        if (reverted) carriesEdit = false
        askingToRevert = false
    }

    content(
        EditorHostActions(
            leave = leave,
            done = done,
            askRevert = { askingToRevert = true },
            // A clean document switches with no dialog at all, which is the ordinary
            // case; a dirty one asks, because the rebuild the host does on a frame
            // change would discard it silently otherwise (ME-158).
            selectFrame = { next -> if (isDirty) pendingFrame = next else onFrameIndex(next) },
            // Live while there is anything to take back: what the file carries, or what
            // this sitting has done and not saved. Both end at the same picture.
            canRevert = carriesEdit || isDirty
        )
    )

    EditorHostDialogs(
        askingToRevert = askingToRevert,
        onRevert = revert,
        askingToDiscard = askingToDiscard,
        onDiscard = { askingToDiscard = false; onClose() },
        pendingFrame = pendingFrame,
        onChangeFrame = { pendingFrame?.let(onFrameIndex); pendingFrame = null },
        onKeepEditing = { askingToRevert = false; askingToDiscard = false; pendingFrame = null }
    )
}
