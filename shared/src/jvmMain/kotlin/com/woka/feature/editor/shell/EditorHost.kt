package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.woka.feature.editor.core.EditSaveResult
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorEngineFactory
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorToolRegistry
import kotlinx.coroutines.launch
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
    DisposableEffect(session) { onDispose { session.close() } }

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
    // choose** (§4.5): no target, no name, no confirmation — which is why this is four
    // lines and why `copyNameFor` no longer exists. The banner is ME-130.
    val done = {
        scope.launch {
            val result = engine.save(session.document.value)
            if (result is EditSaveResult.Saved) onSaved(result.path)
            onClose()
        }
        Unit
    }

    // Revert removes the recipe and then tells the session, in that order: the engine
    // owns the file and the session only mirrors it (ME-107). A failed write leaves
    // both alone, so the editor never claims a revert the disk refused.
    val revert = {
        scope.launch {
            if (engine.revert() !is EditSaveResult.Failed) {
                carriesEdit = false
                session.revertedToOriginal()
            }
            askingToRevert = false
        }
        Unit
    }

    EditorKeyEffect(
        globalShortcuts = LocalGlobalShortcuts.current,
        canUndo = canUndo,
        canRedo = canRedo,
        onCancel = leave,
        onUndo = session::undo,
        onRedo = session::redo,
        onDone = done
    )

    EditorScreen(
        session = session,
        engine = engine,
        registry = dependencies.registry,
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
