package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTool

/**
 * What is drawn *over* the medium, as one composable rather than as a lambda inside
 * `EditorScreen`.
 *
 * Lifted out when ME-156 needed room in that file for the frame scope strip. It is a
 * move, not a redesign: every argument was already in scope at the call site and the
 * body is unchanged — but it is also the better shape, because the screen now hands
 * `EditorCanvasHost` a named overlay instead of eighteen lines of closure.
 *
 * [document] is the one the canvas actually **drew** — a canvas tool's own operation
 * is held out of it — and the preview matrix is taken of that same document, because
 * an overlay placing a stored rectangle against the whole document would draw it
 * where the picture is not (ME-109, 2026-08-22).
 */
@Composable
internal fun EditorCanvasOverlay(
    tools: List<EditorTool>,
    activeTool: EditorTool?,
    session: EditorSession,
    engine: EditorEngine,
    document: EditDocument,
    geometry: EditorCanvasGeometry,
    canvasKeys: CanvasKeys?,
    selection: ToolSelection
) {
    ActiveToolOverlay(
        // With nothing open, the tool that owns a mark is mounted anyway, so the mark
        // can still be moved (ME-149) — `idleCanvasTool` holds why it is one tool and
        // not all of them.
        tool = activeTool ?: idleCanvasTool(tools, document),
        selectOnly = activeTool == null,
        session = session,
        geometry = geometry,
        previewMatrix = engine.previewMatrix(document),
        canvasKeys = canvasKeys,
        selection = selection
    )
}
