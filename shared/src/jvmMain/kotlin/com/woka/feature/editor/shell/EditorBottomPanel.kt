package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolOp

/**
 * Everything the editor stacks along the bottom edge: the two notices, the open tool's
 * inspector, and the rail.
 *
 * Split from `EditorScreen` when ME-114's selection plumbing took that file past the size
 * mandate. The seam is the one the layout already had — a canvas that fills the window and
 * one column floating over its bottom edge — and it keeps `EditorScreen` to what it is
 * actually for: owning `activeToolId` and joining the canvas to the panel.
 *
 * **It still names no `MediaKind` and no raster type.** The engine arrives as an interface
 * and is asked for a frame and a thumbnail, which is the same restraint `EditorScreen`
 * documents; moving the code did not move that boundary.
 */
@Composable
internal fun BoxScope.EditorBottomPanel(
    session: EditorSession,
    engine: EditorEngine,
    registry: EditorToolRegistry,
    document: EditDocument,
    tools: List<EditorTool>,
    activeTool: EditorTool?,
    activeToolId: String?,
    skipped: List<ToolOp>,
    unavailable: Map<String, String>,
    /** What the canvas has selected, for `CanvasTool.restyled` (ME-114). */
    selectedId: String?,
    onSelectTool: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = EditorTokens.Rail.bottomInset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EditorTokens.Inspector.bottomInset)
    ) {
        // Bucket B, for the whole session: the cost of `Done` on a format with no
        // carrier is stated before the work, never after it (§4.5).
        if (engine.capability == EditCapability.Destructive) {
            EditorNoHistoryNotice()
        }
        // Above the inspector and independent of it: the warning has to be visible
        // whether or not a tool happens to be open.
        if (skipped.isNotEmpty()) {
            EditorSkippedNotice(skipped.size, skippedToolTitles(skipped, registry))
        }
        if (activeTool != null) {
            // Only the screen may name the frame; a tool is handed it (ME-110).
            val frame = engine.drawnSize(document)
            EditorInspector(
                title = activeTool.manifest.title,
                footnote = activeTool.footnote(paramsOf(document, activeTool), frame),
                content = {
                    ParamRows(
                        tool = activeTool,
                        session = session,
                        document = document,
                        frame = frame,
                        selectedId = selectedId
                    ) { preview, modifier ->
                        engine.Thumbnail(preview, modifier)
                    }
                }
            )
        }
        EditorRail(
            tools = tools,
            activeToolId = activeToolId,
            unavailable = unavailable,
            onSelect = onSelectTool
        )
    }
}
