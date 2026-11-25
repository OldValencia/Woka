package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorEngine
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.panelDocument

/**
 * One tool's part of the inspector: its header actions, its schema-rendered rows, and the
 * line under them (RD-201).
 *
 * **No plate of its own.** It was a card floating over the picture; it now stands on the
 * inspector's panel, which is the plate. The rows are ME-102's — this owns the frame around
 * them and knows nothing about which controls appear inside, the split §3.4 depends on: a
 * tool contributes rows, never a panel.
 *
 * The tool's name is drawn only when [showTitle] — a tab holding two tools (Transform's crop
 * and rotate/flip) — since a single-tool tab is already named by its tab.
 */
@Composable
internal fun EditorToolSection(
    session: EditorSession,
    engine: EditorEngine,
    document: EditDocument,
    tool: EditorTool,
    showTitle: Boolean,
    /** What the canvas has selected — read live, see `ParamRows`. */
    selectedId: () -> String?
) {
    // Only the screen may name the frame; a tool is handed it (ME-110).
    val frame = engine.drawnSize(document)
    val picture = PictureFacts(frame, engine.sourceSize, engine.stats, engine::playHeadMs, engine.timelineMs(document))
    // The tool's own line, then the medium's word on it (`EditorEngine.toolNote`).
    val footnote = listOfNotNull(
        tool.footnote(paramsOf(session.panelDocument(document, tool.manifest), tool, engine.sourceSize), frame),
        engine.toolNote(tool.manifest.id)
    ).joinToString(" · ").ifEmpty { null }

    Column(
        modifier = Modifier.fillMaxWidth().testTag("editor-inspector"),
        verticalArrangement = Arrangement.spacedBy(EditorTokens.Inspector.rowGap)
    ) {
        // The tool's actions about its rows are drawn by `ParamRows` now, above and under them (RD-202).
        if (showTitle) SectionTitle(tool.manifest.title)
        ParamRows(
            tool = tool,
            session = session,
            document = document,
            picture = picture,
            selectedId = selectedId
        ) { preview, modifier ->
            engine.Thumbnail(preview, modifier)
        }
        if (footnote != null) {
            BasicText(
                text = footnote,
                modifier = Modifier.testTag("editor-inspector-footnote"),
                style = TextStyle(color = Theme.room.muted, fontSize = 12.sp)
            )
        }
    }
}

/**
 * The tool's name, when the tab holds two tools.
 *
 * **Not `Reset` for the document** — removed 2026-08-19: it cleared every tool's work from a
 * header naming one. What it was reached for is `Revert to original…`, at the inspector's foot.
 */
@Composable
private fun SectionTitle(title: String) {
    BasicText(
        text = title,
        modifier = Modifier.padding(bottom = 6.dp).testTag("editor-inspector-title"),
        style = TextStyle(color = Theme.room.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    )
}
