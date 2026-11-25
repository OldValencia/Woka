package com.woka.feature.editor.shell

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
    panel: EditorPanelState = remember { EditorPanelState() }
) {
    val document by session.document.collectAsState()
    val canUndo by session.canUndo.collectAsState()
    val canRedo by session.canRedo.collectAsState()
    val tools = toolsForPicture(registry, engine)
    val peek = remember { PeekState() }
    // Hoisted out of the overlay's scope (ME-114): the inspector restyles what the canvas selected.
    val selection = remember(session) { ToolSelection() }
    val accessory = remember { InspectorAccessory() }

    // The editor takes over the shell's bar rather than drawing a second one — see EditorTopBarSetup.
    EditorTopBarSetup(
        // "This photograph carries an edit", not `isDirty`, which is the exit dialog's question.
        isEdited = !document.isEmpty,
        canUndo = canUndo,
        canRedo = canRedo,
        peek = peek,
        panel = panel,
        onLeave = onCancel,
        onUndo = session::undo,
        onRedo = session::redo,
        onDone = onDone
    )

    // One `if`, and a photograph pays exactly this much for the frame band existing. A
    // film's clips are counted on the document, which every split moves (ME-215).
    val clipSelection by session.clipSelection.collectAsState()
    val perFrame = frameControls?.let { it.copy(frames = engine.framesIn(document).takeIf { f -> f.clips } ?: it.frames) }
        ?.takeIf { it.frames.allowsPerFrame }?.copy(selectedClip = engine.clipNumberOf(document, clipSelection))
    val scope = perFrame?.scope ?: EditScope.AllFrames
    val blocked = unavailable + engine.unavailableIn(scope)

    val tabs = rememberEditorTabs(tools, blocked)
    val tab = tabs.tab
    // The canvas answers to the open tab's tool while the inspector is out; hidden (F6), the
    // picture is only looked at.
    val activeTool = tab?.canvasTool?.takeIf { panel.shown }
    // A whole-film tool opening clears a film's clip selection, as a step (ME-221).
    LaunchedEffect(activeTool?.manifest?.id) {
        if (engine.frames.clips && clearsClipSelection(activeTool?.manifest?.domain)) session.pickClip(null, NO_CLIP)
    }
    val canvasDocument = canvasDocumentFor(activeTool, document, peek.active)
    val skipped = skippedOps(document, registry, engine.kind)

    // Out from nothing on entry, so the panel arrives beside the picture rather than appearing.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val panelWidth by animateDpAsState(
        if (entered && panel.shown) EditorTokens.Panel.width + 1.dp else 0.dp,
        tween(250, easing = FastOutSlowInEasing)
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.room.room)
            .testTag("editor-screen")
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            EditorCanvasLayer(engine, session, tools, activeTool, canvasDocument, perFrame, canvasKeys, selection, peek)
            if (perFrame != null) EditorFrameBand(perFrame, session.media.file)
            EditorBottomPanel(session, engine, registry, document, tools, activeTool, skipped) { selection.id }
            EditorAccessoryFloat(accessory)
        }
        Box(modifier = Modifier.width(panelWidth).fillMaxHeight().clipToBounds()) {
            Row(modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true)) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(Theme.room.line))
                CompositionLocalProvider(LocalInspectorAccessory provides accessory) {
                    EditorInspectorPanel(
                        tabs = tabs.tabs,
                        tab = tab,
                        unavailable = blocked,
                        onSelectTab = tabs::choose,
                        session = session,
                        engine = engine,
                        document = document,
                        selectedId = { selection.id },
                        canRevert = canRevert,
                        onRevert = onRevert
                    )
                }
            }
        }
    }
}
