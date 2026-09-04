package com.woka.feature.editor.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTool

/**
 * The open tool's card: its header, its schema-rendered rows, and the panel that slides
 * out beside it.
 *
 * **Split from [EditorBottomPanel] by ME-206**, which needed one line in that file's
 * column and found it at 149 of the mandate's 150. The seam is the one the layout
 * already had: what moved is *the card*, and what stayed is *the column* — the notices,
 * the band and the rail, none of which reads anything this needs. No behaviour changed
 * with it, which is what the inspector's own UI tests are for.
 *
 * [railWidth] is measured by the column, not here: the dock's width is however many
 * tools are enabled, and this card's ceiling is derived from it.
 */
@Composable
internal fun EditorInspectorCard(
    session: EditorSession,
    engine: EditorEngine,
    document: EditDocument,
    activeTool: EditorTool,
    railWidth: Dp,
    accessory: InspectorAccessory,
    /** What the canvas has selected — read live, see `ParamRows`. */
    selectedId: () -> String?
) {
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

    val picture = PictureFacts(frame, engine.sourceSize, engine.stats)
    CompositionLocalProvider(LocalInspectorAccessory provides accessory) {
        GooeyPair(
            progress = progress,
            // Kept composed until the spring has finished retracting, so closing
            // is a movement rather than a disappearance.
            extra = if (extra != null || progress > 0.01f) extra else null
        ) { plateAlpha ->
            EditorInspector(
                title = activeTool.manifest.title,
                footnote = activeTool.footnote(paramsOf(document, activeTool, engine.sourceSize), frame),
                // The dock is the ceiling: a card sizes to its content and stops
                // there, so Styles' fourteen squares reach the rail's width while
                // Transform's four buttons stay compact — the reason this was a
                // range and never a number.
                maxWidth = ceiling,
                plateAlpha = plateAlpha,
                // Beside the title, because these are about the card (ME-174).
                actions = { InspectorHeaderActions(activeTool, session, picture, selectedId) },
                content = {
                    ParamRows(
                        tool = activeTool,
                        session = session,
                        document = document,
                        picture = picture,
                        selectedId = selectedId
                    ) { preview, modifier ->
                        engine.Thumbnail(preview, modifier)
                    }
                }
            )
        }
    }
}
