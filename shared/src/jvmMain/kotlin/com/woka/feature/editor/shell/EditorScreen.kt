package com.woka.feature.editor.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.woka.core.data.MediaKind
import com.woka.core.ui.components.OriginalCompare
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.NO_CLIP
import com.woka.feature.editor.core.pickClip

/**
 * The editor, whole: the canvas in the room, and the inspector beside it (RD-201).
 *
 * **It owns which tab is open and nothing else** ([EditorTabState]). Every other piece of
 * state belongs to the session or the registry, which is what makes the screen re-render
 * correctly when a tool is switched off in Settings while it is open.
 *
 * **It does not mount itself and does not save.** ME-106 places it and takes the keyboard;
 * ME-104 decides what [onDone] means. Both are callbacks here so that the video editor
 * composes the same function. Nothing in this file names a `MediaKind`.
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
    frameControls: EditorFrameControls? = null,
    /** Built above as well, so F6 — handled there — reaches it. */
    panel: EditorPanelState = remember { EditorPanelState() },
    /** `Original` — built above too, so `\` and Esc reach it (RD-502). */
    original: OriginalCompare = remember { OriginalCompare() }
) {
    val document by session.document.collectAsState()
    val canUndo by session.canUndo.collectAsState()
    val canRedo by session.canRedo.collectAsState()
    val tools = toolsForPicture(registry, engine)
    // Hoisted out of the overlay's scope (ME-114): the inspector restyles what the canvas selected.
    val selection = remember(session) { ToolSelection() }
    var helpOpen by remember { mutableStateOf(false) }

    // The editor takes over the shell's bar rather than drawing a second one — see EditorTopBarSetup.
    EditorTopBarSetup(
        // "This photograph carries an edit", not `isDirty`, which is the exit dialog's question.
        isEdited = !document.isEmpty,
        canUndo = canUndo,
        canRedo = canRedo,
        original = original,
        panel = panel,
        onLeave = onCancel,
        onUndo = session::undo,
        onRedo = session::redo,
        onDone = onDone,
        onHelp = { helpOpen = true }
    )

    // One `if`, and a photograph pays exactly this much for the frame band existing. A
    // film's clips are counted on the document, which every split moves (ME-215).
    val clipSelection by session.clipSelection.collectAsState()
    val perFrame = frameControls?.let { it.copy(frames = engine.framesIn(document).takeIf { f -> f.clips } ?: it.frames) }
        ?.takeIf { it.frames.allowsPerFrame }
    val scope = perFrame?.scope ?: EditScope.AllFrames
    val blocked = unavailable + engine.unavailableIn(scope)

    // Clip's tab only while a clip is selected (RD-302).
    val clipOnly = if (clipSelection == null) tools.filter { it.manifest.clipOnly }.map { it.manifest.id }.toSet() else emptySet()
    val tabs = rememberEditorTabs(tools, blocked, clipOnly)
    val tab = tabs.tab
    val film = rememberFilmSelection(session, tabs, selection, engine.frames.clips, canvasKeys)
    // The scope line (RD-204): a frame set's switch, or any film's crumbs — cut or not.
    val scopeLine = (perFrame ?: frameControls?.takeIf { it.frames.clips })
        ?.copy(clip = engine.clipScopeOf(document, clipSelection), shape = film.shapeLabel(engine, document))
    // The canvas answers to the open tab's tool while the inspector is out; hidden (F6), the
    // picture is only looked at.
    val activeTool = tab?.canvasTool?.takeIf { panel.shown }
    // A whole-film tool opening clears a film's clip selection, as a step (ME-221).
    LaunchedEffect(activeTool?.manifest?.id) {
        if (engine.frames.clips && clearsClipSelection(activeTool?.manifest?.domain)) session.pickClip(null, NO_CLIP)
    }
    val canvasDocument = canvasDocumentFor(activeTool, document, original.active)
    val skipped = skippedOps(document, registry, engine.kind)
    val onCopy = LocalDoneChoices.current.onCopy
    if (helpOpen) {
        EditorHelpSheet(
            video = engine.kind == MediaKind.Video,
            onStep = { step ->
                helpOpen = false
                tabs.follow(step, session, onCopy)
            },
            onDismiss = { helpOpen = false }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.room.room)
            .testTag("editor-screen")
    ) {
        // The canvas over the medium's timeline, docked under it beside the inspector (RD-301).
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                EditorCanvasLayer(engine, session, tools, activeTool, canvasDocument, perFrame, canvasKeys, selection, original)
                EditorFrameBand(perFrame, engine.frameTransport, session.media.file)
                EditorBottomPanel(session, engine, registry, skipped)
            }
            engine.Timeline(document, timelineScopeOf(session, tools, clipSelection, tabs, selection), Modifier.fillMaxWidth())
        }
        EditorInspectorDock(
            panel = panel,
            scope = scopeLine?.let { line -> { EditorScopeLine(line, film::wholeVideo) } },
            tabs = tabs,
            unavailable = blocked,
            session = session,
            engine = engine,
            document = document,
            selection = selection,
            selectedClip = clipSelection,
            canRevert = canRevert,
            onRevert = onRevert
        )
    }
}
