package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
            // **No `weight` here, deliberately** (found by bisecting 2026-08-28). A
            // `Row` holding a weighted child expands to the whole width offered to it,
            // so the card stopped being centred by the column above and sat against the
            // window's edge — which is what made half the inspector's UI tests report
            // their rows as "not displayed". The narrowing is arithmetic instead.
            val wide = activeTool.wideInspector && railWidth > 0.dp
            // **`wideInspector` raises the ceiling, it never lowers it** (measured
            // 2026-08-29). It was `railWidth` outright, which is a cap: with a narrow
            // dock — one tool in it — Markup's card was squeezed to 212 dp and its
            // swatches laid out at zero width, so every click on one went nowhere.
            val room = maxOf(railWidth, EditorTokens.Inspector.maxWidth)
            val ceiling = if (wide) room else EditorTokens.Inspector.maxWidth
            val extra = accessory.content
            // Springy rather than eased, which is what makes the panel read as pulled out
            // of the card rather than faded in beside it. It overshoots a little and
            // settles — the motion the `gooey examples` were sent for.
            val progress by animateFloatAsState(
                targetValue = if (extra == null) 0f else 1f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 300f)
            )

            CompositionLocalProvider(LocalInspectorAccessory provides accessory) {
                GooeyPair(
                    progress = progress,
                    // Kept composed until the spring has finished retracting, so closing
                    // is a movement rather than a disappearance.
                    extra = if (extra != null || progress > 0.01f) extra else null
                ) { plateAlpha ->
                    EditorInspector(
                        title = activeTool.manifest.title,
                        footnote = activeTool.footnote(paramsOf(document, activeTool), frame),
                        // The dock is the ceiling: a card sizes to its content and stops
                        // there, so Styles' fourteen squares reach the rail's width while
                        // Transform's four buttons stay compact — the reason this was a
                        // range and never a number.
                        maxWidth = ceiling,
                        plateAlpha = plateAlpha,
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
            }
        }
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
