package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.woka.app.shell.LocalGlobalShortcuts
import com.woka.core.data.MediaItem
import com.woka.feature.editor.EditorTarget
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorEngineFactory
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.moveToFrame
import com.woka.feature.editor.core.detachFrame
import com.woka.feature.editor.core.rebase
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
 * `Done` and `Revert` are `EditorActions`'; the questions asked before either, and the
 * dialogs that ask them, are `EditorHostControls`'. What is left here is **building**
 * the editor — the engine, the document, the session, the frame — rather than acting
 * on it, which is why every `remember` below is keyed and none of them is a verb.
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

    val engine: EditorEngine? = remember(target.path, frameIndex) {
        dependencies.factory.create(target.path, frameIndex)
    }

    if (engine == null) {
        LaunchedEffect(target) { onClose() }
        return
    }

    val file = remember(target.path) { File(target.path) }
    val session = rememberEditorSession(target.path, frameIndex, file, engine)

    // **Seeded from the file** (ME-156), and **once per sitting, not per frame**
    // (2026-09-02): keyed on the frame it re-seeded to `All Frames` on every move and
    // the switch flipped under the user's hand. Re-seeding guarded against an
    // `All Frames` save clearing other frames' work, which ME-175 made impossible.
    var editScope by remember(target.path) { mutableStateOf(engine.frames.storedScope) }

    // The engine is still keyed on the frame — it draws one frame's pixels — but the
    // session is not, so it is told to look elsewhere rather than rebuilt. **After the
    // switch is declared**, because what a frame change means depends on it.
    LaunchedEffect(frameIndex) { session.moveToFrame(frameIndex, editScope) }

    EditorPresenceEffect(engine, session, file.name, editScope, onClose, onSaved)

    val canUndo by session.canUndo.collectAsState()
    val canRedo by session.canRedo.collectAsState()

    // Built here so its lifetime is the editor's: the key handler below reads it, and
    // the active canvas tool — two composables down — writes it (ME-114).
    val canvasKeys = remember(session) { CanvasKeys() }

    EditorHostControls(
        engine = engine,
        session = session,
        file = file,
        editScope = editScope,
        frameIndex = frameIndex,
        // **What the file carries, not what this frame draws** (2026-09-02): read off
        // the open frame, a base-less file whose frame 3 held a crop looked unedited.
        carriesStoredEdit = !engine.storedBase().isEmpty || engine.frames.overridden.isNotEmpty(),
        onFrameIndex = { frameIndex = it },
        onClose = onClose,
        onSaved = onSaved
    ) { actions ->
        EditorKeyEffect(
            globalShortcuts = LocalGlobalShortcuts.current,
            canUndo = canUndo,
            canRedo = canRedo,
            onCancel = actions.leave,
            onUndo = session::undo,
            onRedo = session::redo,
            onDone = actions.done,
            canvasKeys = canvasKeys
        )

        EditorScreen(
            session = session,
            engine = engine,
            registry = dependencies.registry,
            canvasKeys = canvasKeys,
            onCancel = actions.leave,
            onDone = actions.done,
            canRevert = actions.canRevert,
            onRevert = actions.askRevert,
            frameControls = EditorFrameControls(
                frames = engine.frames,
                scope = editScope,
                frameIndex = frameIndex,
                // Leaving All Frames fixes what was made there as the shared layer, so a
                // frame reached afterwards still shows it (2026-09-02).
                // The switch moves what is *shown*, not only what a save means: leaving
                // All Frames fixes the layer, entering it puts the frame's own work aside
                // and shows the layer alone (2026-09-02).
                onScopeChange = { picked ->
                    if (picked == EditScope.SingleFrame) session.rebase() else session.detachFrame()
                    editScope = picked
                },
                onSelectFrame = actions.selectFrame
            )
        )
    }
}
