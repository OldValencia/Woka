package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.woka.app.shell.EditorPresence
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
 * **It owns the session's lifetime**, which ME-004 made an explicit obligation: the
 * session builds a `CoroutineScope` and whoever constructs it must `close()` it. That
 * is a `DisposableEffect` here, and it is exactly the ownership ME-128 moves to a store
 * when more than one editor can be open at once — the reason this is one composable
 * rather than three call sites.
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
    val engine: EditorEngine? = remember(target) {
        dependencies.factory.create(target.path, target.frameIndex)
    }

    if (engine == null) {
        LaunchedEffect(target) { onClose() }
        return
    }

    val file = remember(target) { File(target.path) }
    // Opened on what the file already holds (ME-134): a session starting empty made
    // the second `Done` on a photograph replace the first one's edit instead of
    // continuing it. A few kilobytes off the file's head, once per open.
    val stored = remember(target) { engine.storedDocument() }
    val session = remember(target) { EditorSession(MediaItem.Image(file), stored) }

    // What the video window invokes on its way back to the main one (ME-124). Held
    // through `rememberUpdatedState` so the published lambda always runs the current
    // `onClose`/`onSaved`, and reads the document when called rather than now.
    val leaveForVideo by rememberUpdatedState(
        leaveForVideoAction(engine, session, file.name, onClose, onSaved)
    )
    DisposableEffect(session) {
        EditorPresence.opened { leaveForVideo() }
        onDispose {
            EditorPresence.closed()
            session.close()
        }
    }

    // Whether the *file* still carries something to remove — read once, and moved by
    // the only thing in this session that can change it (ME-107).
    var carriesEdit by remember(target) { mutableStateOf(!stored.isEmpty) }
    var askingToRevert by remember { mutableStateOf(false) }

    val isDirty by session.isDirty.collectAsState()
    val canUndo by session.canUndo.collectAsState()
    val canRedo by session.canRedo.collectAsState()
    var askingToDiscard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val leave = { if (isDirty) askingToDiscard = true else onClose() }

    // `Done` writes into the file that was opened and closes. **There is nothing to
    // choose** (§4.5): no target, no name, no confirmation — which is why this is one
    // line and why `copyNameFor` no longer exists. The order it closes in, and the
    // scope the write outlives this composable in, are `saveAction`'s (ME-130).
    val done = saveAction(engine, session, file.name, onClose, onSaved)

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
        onRevert = { askingToRevert = true }
    )

    if (askingToRevert) {
        RevertDialog(onRevert = revert, onKeepEditing = { askingToRevert = false })
    }

    if (askingToDiscard) {
        DiscardChangesDialog(
            onDiscard = { askingToDiscard = false; onClose() },
            onKeepEditing = { askingToDiscard = false }
        )
    }
}
