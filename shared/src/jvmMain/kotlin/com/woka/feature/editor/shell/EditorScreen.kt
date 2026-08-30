package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorToolRegistry

/**
 * The editor, whole.
 *
 * **It owns [activeToolId] and nothing else.** Every other piece of state belongs to
 * the session or the registry, which is what makes the screen re-render correctly when
 * a tool is switched off in Settings while it is open, with no wiring of its own.
 *
 * **It does not mount itself and does not save.** ME-106 places it and takes the
 * keyboard; ME-104 decides what [onDone] means. Both are callbacks here so that the
 * video editor composes the same function.
 *
 * The medium appears exactly once, as [EditorEngine.Surface] inside the canvas host.
 * Nothing in this file names a `MediaKind`: the registry is asked with `engine.kind`,
 * and that is the only place a kind is spoken about at all.
 */
@Composable
internal fun EditorScreen(
    session: EditorSession,
    engine: EditorEngine,
    registry: EditorToolRegistry,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    canRevert: Boolean = false,
    onRevert: () -> Unit = {},
    unavailable: Map<String, String> = emptyMap(),
    /** The open canvas tool's keyboard slot — built above, because the handler is (ME-114). */
    canvasKeys: CanvasKeys? = null,
    /** Null for a file holding one picture, which pays one comparison for it (ME-156). */
    frameControls: EditorFrameControls? = null
) {
    val document by session.document.collectAsState()
    val canUndo by session.canUndo.collectAsState()
    val canRedo by session.canRedo.collectAsState()
    val tools by registry.tools(engine.kind).collectAsState()

    var activeToolId by remember { mutableStateOf<String?>(null) }

    // **Hoisted out of the overlay's scope** (ME-114). The inspector needs it too, so that
    // choosing a colour with a shape selected restyles that shape — and a selection living
    // inside the scope would be rebuilt, and forgotten, several times a second.
    val selection = remember(session) { ToolSelection() }

    // The editor takes over the shell's bar rather than drawing a second one, which
    // is also what removes the folder path and the viewer's own controls from above
    // it — see EditorTopBarSetup.
    EditorTopBarSetup(
        // "This photograph carries an edit", which includes the one already in the
        // file — not "there is unsaved work", which is `isDirty` and the exit dialog's.
        isEdited = !document.isEmpty,
        canUndo = canUndo,
        canRedo = canRedo,
        canRevert = canRevert,
        onLeave = onCancel,
        onRevert = onRevert,
        onUndo = session::undo,
        onRedo = session::redo,
        onDone = onDone
    )

    // A tool switched off in Settings leaves `tools`, and an inspector for a tool that
    // is gone would keep drawing controls whose writes the fold then skips. Clearing
    // here rather than in the click handler covers the case nobody clicked.
    LaunchedEffect(tools) {
        if (activeToolId != null && tools.none { it.manifest.id == activeToolId }) {
            activeToolId = null
        }
    }

    val activeTool = tools.firstOrNull { it.manifest.id == activeToolId }
    val canvasDocument = canvasDocumentFor(activeTool, document)
    val skipped = skippedOps(document, registry, engine.kind)

    // One `if`, and a photograph pays exactly this much for the frame band existing.
    val perFrame = frameControls?.takeIf { it.frames.allowsPerFrame }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // The app's own surface, not black: a transparent PNG or GIF sat on a
            // black plate here while the viewer one panel below showed it on this.
            .background(Theme.colors.background)
            .testTag("editor-screen")
    ) {
        EditorCanvasHost(
            engine = engine,
            document = canvasDocument,
            modifier = Modifier.fillMaxSize().padding(start = canvasInsetFor(perFrame)),
            // Back to Fit whenever the tool changes: a crop opened at 100 % on a
            // corner of the picture is a crop nobody can frame (§4.3).
            resetKey = activeToolId,
            // A `CanvasTool` draws over the medium and takes the pointer first; every
            // other tool leaves this empty and the canvas pans as it always did.
            overlay = { geometry ->
                EditorCanvasOverlay(
                    tools = tools,
                    activeTool = activeTool,
                    session = session,
                    engine = engine,
                    document = canvasDocument,
                    geometry = geometry,
                    canvasKeys = canvasKeys,
                    selection = selection
                )
            }
        )

        if (perFrame != null) EditorFrameBand(perFrame, session.media.file)

        EditorBottomPanel(
            session = session,
            engine = engine,
            registry = registry,
            document = document,
            tools = tools,
            activeTool = activeTool,
            activeToolId = activeToolId,
            skipped = skipped,
            unavailable = unavailable,
            selectedId = { selection.id },
            onSelectTool = { id -> activeToolId = if (activeToolId == id) null else id }
        )
    }
}
