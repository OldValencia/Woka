package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.woka.app.shell.LocalGlobalShortcuts
import com.woka.core.data.MediaItem
import com.woka.feature.editor.EditorTarget
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorEngineFactory
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorToolRegistry
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

private class EditorDependencies : KoinComponent {
    val factory: EditorEngineFactory by inject()
    val registry: EditorToolRegistry by inject()
}

/**
 * Everything between "a path" and "a running editor".
 *
 * **The engine, the stored document and the session are keyed on the path *and the
 * frame*** (ME-158): walking to another picture inside the same file rebuilds all
 * three, because every one of them is about one frame. The frame itself is keyed on
 * the path alone, so re-entering the same file keeps the frame the user walked to
 * while a different file starts at its own.
 *
 * Session lifetime and the return from the video window are `EditorPresenceEffect`'s;
 * the three confirmations are `EditorHostDialogs`'; `Done` and `Revert` are
 * `EditorActions`'. What is left here is the state they all read.
 *
 * A target the factory refuses closes immediately rather than showing an empty shell.
 * Both doors already ask [isEditable] before opening one, so this is the case where the
 * file changed between the menu opening and the click landing.
 */
@Composable
internal fun EditorHost(
    target: EditorTarget,
    onClose: () -> Unit,
    onSaved: (String) -> Unit
) {
    val dependencies = remember { EditorDependencies() }
    var frameIndex by remember(target.path) { mutableStateOf(target.frameIndex) }
    var pendingFrame by remember(target.path) { mutableStateOf<Int?>(null) }

    val engine: EditorEngine? = remember(target.path, frameIndex) {
        dependencies.factory.create(target.path, frameIndex)
    }

    if (engine == null) {
        LaunchedEffect(target) { onClose() }
        return
    }

    val file = remember(target.path) { File(target.path) }
    // Opened on what the file already holds (ME-134): a session starting empty made
    // the second `Done` on a photograph replace the first one's edit instead of
    // continuing it. A few kilobytes off the file's head, once per open.
    val stored = remember(target.path, frameIndex) { engine.storedDocument() }
    val session = remember(target.path, frameIndex) { EditorSession(MediaItem.Image(file), stored) }

    // **Seeded from the file, never from a constant** (ME-156), and re-seeded on every
    // frame change: reopening frame 3 of a burst that already carries a frame-3
    // override must come back in `This Frame`, or a `Done` the user thinks changed one
    // picture clears every other frame's work.
    var editScope by remember(target.path, frameIndex) {
        mutableStateOf(engine.frames.storedScope)
    }

    EditorPresenceEffect(engine, session, file.name, editScope, onClose, onSaved)

    // Whether the *file* still carries something to remove — read once, and moved by
    // the only thing in this session that can change it (ME-107).
    var carriesEdit by remember(target.path, frameIndex) { mutableStateOf(!stored.isEmpty) }
    var askingToRevert by remember { mutableStateOf(false) }

    val isDirty by session.isDirty.collectAsState()
    val canUndo by session.canUndo.collectAsState()
    val canRedo by session.canRedo.collectAsState()
    var askingToDiscard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val leave = { if (isDirty) askingToDiscard = true else onClose() }

    // `Done` writes into the file that was opened and closes. **There is nothing to
    // choose** (§4.5) beyond which frames it is for. The order it closes in, and the
    // scope the write outlives this composable in, are `saveAction`'s (ME-130).
    val done = saveAction(engine, session, file.name, editScope, onClose, onSaved)

    val revert = revertAction(engine, session, scope) { reverted ->
        if (reverted) carriesEdit = false
        askingToRevert = false
    }

    // Built here so its lifetime is the editor's: the key handler installed just below
    // reads it, and the active canvas tool — two composables down — writes it (ME-114).
    val canvasKeys = remember(session) { CanvasKeys() }

    EditorKeyEffect(
        globalShortcuts = LocalGlobalShortcuts.current,
        canUndo = canUndo,
        canRedo = canRedo,
        onCancel = leave,
        onUndo = session::undo,
        onRedo = session::redo,
        onDone = done,
        canvasKeys = canvasKeys
    )

    EditorScreen(
        session = session,
        engine = engine,
        registry = dependencies.registry,
        canvasKeys = canvasKeys,
        onCancel = leave,
        onDone = done,
        // Enabled while there is anything to take back: what the file carries, or what
        // this sitting has done and not saved. Both end at the same picture.
        canRevert = carriesEdit || isDirty,
        onRevert = { askingToRevert = true },
        frameControls = EditorFrameControls(
            frames = engine.frames,
            scope = editScope,
            frameIndex = frameIndex,
            onScopeChange = { editScope = it },
            // A clean document switches with no dialog at all, which is the ordinary
            // case; a dirty one asks, because the rebuild above would discard it
            // silently otherwise (ME-158).
            onSelectFrame = { next -> if (isDirty) pendingFrame = next else frameIndex = next }
        )
    )

    EditorHostDialogs(
        askingToRevert = askingToRevert,
        onRevert = revert,
        askingToDiscard = askingToDiscard,
        onDiscard = { askingToDiscard = false; onClose() },
        pendingFrame = pendingFrame,
        onChangeFrame = { frameIndex = pendingFrame ?: frameIndex; pendingFrame = null },
        onKeepEditing = { askingToRevert = false; askingToDiscard = false; pendingFrame = null }
    )
}
