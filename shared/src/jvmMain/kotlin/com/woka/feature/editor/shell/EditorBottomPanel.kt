package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * What the editor still stacks along the canvas's bottom edge: the two notices and the
 * medium's own band (RD-201). The rail and the floating card left for the inspector on the
 * right; these stay over the picture, so they keep `ViewerChrome`'s scrims.
 *
 * **It still names no `MediaKind` and no raster type.** The engine arrives as an interface
 * and is asked for its band, which a still's engine leaves empty.
 */
@Composable
internal fun BoxScope.EditorBottomPanel(
    session: EditorSession,
    engine: EditorEngine,
    registry: EditorToolRegistry,
    document: EditDocument,
    tools: List<EditorTool>,
    activeTool: EditorTool?,
    skipped: List<ToolOp>,
    /** What the canvas has selected — read live, see `ParamRows`. */
    selectedId: () -> String?
) {
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
            .padding(bottom = EditorTokens.Band.bottomInset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EditorTokens.Inspector.bottomInset)
    ) {
        // Bucket B, for the whole session: the cost of `Done` on a format with no
        // carrier is stated before the work, never after it (§4.5).
        when (engine.capability) {
            EditCapability.Destructive -> EditorNoHistoryNotice()
            EditCapability.RenderOnly -> EditorRenderOnlyNotice(session.media.file)
            else -> Unit
        }
        // Independent of the inspector: the warning has to be visible whichever tab is open.
        if (skipped.isNotEmpty()) {
            EditorSkippedNotice(skipped.size, skippedToolTitles(skipped, registry))
        }
        // No branch on media — a still's engine does not override this and draws nothing.
        engine.Timeline(document, timeline, Modifier)
    }
}
