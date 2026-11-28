package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.woka.app.shell.LocalGlobalShortcuts
import com.woka.core.ui.components.OriginalCompare
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession

/**
 * The running editor's keyboard, wired to the engine, the session and the host's ways out.
 *
 * Split out of `EditorHost` at the size mandate when `Original` joined the keys (RD-502):
 * every argument here was a pass-through there, read by nothing else in that file.
 */
@Composable
internal fun EditorHostKeyEffect(
    engine: EditorEngine,
    session: EditorSession,
    actions: EditorHostActions,
    canvasKeys: CanvasKeys,
    panel: EditorPanelState,
    original: OriginalCompare
) {
    EditorKeyEffect(
        globalShortcuts = LocalGlobalShortcuts.current,
        canUndo = session.canUndo.collectAsState().value,
        canRedo = session.canRedo.collectAsState().value,
        onCancel = actions.leave,
        onUndo = session::undo,
        onRedo = session::redo,
        onDone = actions.done,
        onTogglePlayback = engine::togglePlayback,
        onStep = engine::stepPlayHead,
        canvasKeys = canvasKeys,
        onTogglePanel = panel::toggle,
        onTimelineKey = engine::timelineKey,
        original = original
    )
}
