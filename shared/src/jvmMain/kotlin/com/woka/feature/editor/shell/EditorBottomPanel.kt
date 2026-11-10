package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.woka.feature.editor.core.CanvasTool
import com.woka.feature.editor.core.EditCapability
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.EditorToolRegistry
import com.woka.feature.editor.core.ToolDomain
import com.woka.feature.editor.core.ToolOp

/**
 * Everything the editor stacks along the bottom edge: the two notices, the open tool's
 * inspector, the medium's own band, and the rail.
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
    /** What the canvas has selected — read live, see `ParamRows`. */
    selectedId: () -> String?,
    onSelectTool: (String) -> Unit
) {
    // Measured rather than declared: the dock's width is however many tools are
    // enabled, and a constant here would drift the first time one is added (ME-115).
    var railWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    // Owned here because this is the one place that has both the card and the room
    // beside it — see `InspectorAccessory`.
    val accessory = remember { InspectorAccessory() }
    // The band's selection is the session's (ME-221) — apart from the canvas's, because a
    // clip id is not a shape id, and a step of history, because on a film it is the scope.
    val clipSelection by session.clipSelection.collectAsState()
    val timeline = EditorTimelineScopeImpl(
        session = session,
        tools = tools,
        selected = clipSelection,
        editing = activeTool?.manifest?.domain == ToolDomain.Time,
        markedSpan = (activeTool as? CanvasTool)?.spanOf(document, selectedId())
    )

    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = EditorTokens.Rail.bottomInset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EditorTokens.Inspector.bottomInset)
    ) {
        // Bucket B, for the whole session: the cost of `Done` on a format with no
        // carrier is stated before the work, never after it (§4.5).
        when (engine.capability) {
            EditCapability.Destructive -> EditorNoHistoryNotice()
            EditCapability.RenderOnly -> EditorRenderOnlyNotice(session.media.file.extension)
            else -> Unit
        }
        // Above the inspector and independent of it: the warning has to be visible
        // whether or not a tool happens to be open.
        if (skipped.isNotEmpty()) {
            EditorSkippedNotice(skipped.size, skippedToolTitles(skipped, registry))
        }
        if (activeTool != null) {
            EditorInspectorCard(
                session = session,
                engine = engine,
                document = document,
                activeTool = activeTool,
                railWidth = railWidth,
                accessory = accessory,
                selectedId = selectedId
            )
        }
        // **Between the inspector and the rail** (ME-206), so the band and the rail are
        // the two fixed things at the bottom edge and the card opens *above* them. The
        // strip is where a clip is selected, and a selection that jumped under the cursor
        // whenever a tool was picked would be the opposite of the point.
        //
        // No new token: the column's own `spacedBy` gives the gap. No branch on media
        // either — a still's engine does not override this and draws nothing.
        engine.Timeline(document, timeline, Modifier)
        EditorRail(
            tools = tools,
            activeToolId = activeToolId,
            unavailable = unavailable,
            onSelect = onSelectTool,
            modifier = Modifier.onSizeChanged {
                railWidth = with(density) { it.width.toDp() }
            }
        )
    }
}
