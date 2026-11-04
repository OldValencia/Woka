package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.woka.feature.editor.core.ClipLooks
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditScope
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.LocalClipLooks
import com.woka.feature.editor.core.ownOpsOf

/**
 * The picture and whatever draws over it — `EditorScreen`'s canvas, moved out unchanged
 * when ME-215 pushed that file past the size mandate.
 */
@Composable
internal fun EditorCanvasLayer(
    engine: EditorEngine,
    session: EditorSession,
    tools: List<EditorTool>,
    activeTool: EditorTool?,
    document: EditDocument,
    frameControls: EditorFrameControls?,
    canvasKeys: CanvasKeys?,
    selection: ToolSelection,
    peek: PeekState
) {
    // A film draws each clip through its own layer (ME-215); the session holds them.
    val open = session.frame.takeIf { frameControls?.scope == EditScope.SingleFrame }
    val looks = ClipLooks(open) { key -> session.ownOpsOf(key) }.takeIf { engine.frames.clips }
    CompositionLocalProvider(LocalClipLooks provides looks) {
        CanvasHostWith(engine, session, tools, activeTool, document, frameControls, canvasKeys, selection, peek)
    }
}

@Composable
private fun CanvasHostWith(
    engine: EditorEngine,
    session: EditorSession,
    tools: List<EditorTool>,
    activeTool: EditorTool?,
    document: EditDocument,
    frameControls: EditorFrameControls?,
    canvasKeys: CanvasKeys?,
    selection: ToolSelection,
    peek: PeekState
) {
    EditorCanvasHost(
        engine = engine,
        document = document,
        modifier = Modifier.fillMaxSize().padding(start = canvasInsetFor(frameControls)),
        // Back to Fit whenever the tool changes: a crop opened at 100 % on a
        // corner of the picture is a crop nobody can frame (§4.3).
        resetKey = activeTool?.manifest?.id,
        // A `CanvasTool` draws over the medium and takes the pointer first; every
        // other tool leaves this empty and the canvas pans as it always did.
        overlay = { geometry ->
            EditorCanvasOverlay(
                tools = tools,
                activeTool = activeTool,
                session = session,
                engine = engine,
                document = document,
                geometry = geometry,
                canvasKeys = canvasKeys,
                selection = selection,
                peeking = peek.active
            )
        }
    )
}
