package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.CanvasTool
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTool
import androidx.compose.ui.Modifier
import com.woka.feature.editor.core.clipOpen
import com.woka.feature.editor.core.panelDocument

/**
 * A tool's whole panel, from its schema — the composable form of §3.4's claim that a
 * tool writes no UI code.
 *
 * Every row for every tool in the app comes through here, which is why the tools
 * cannot drift apart in spacing, typography or commit behaviour: there is one
 * implementation and it is this one.
 */
@Composable
internal fun ParamRows(
    tool: EditorTool,
    session: EditorSession,
    document: EditDocument,
    /** The frame, the source size and the histogram — see [PictureFacts]. */
    picture: PictureFacts = PictureFacts(),
    /**
     * What the canvas has selected, for `CanvasTool.restyled` (ME-114) — **a function,
     * not a value**.
     *
     * The document below is read at the moment of the write for a reason `ParamWriter`
     * sets out at length, and the selection is the same kind of value: one that can move
     * between a row composing and that row being used. It is passed as a function so the
     * two cannot drift apart — **hardening, not a fix**; no scenario has been shown where
     * a captured value was actually stale (2026-08-26).
     */
    selectedId: () -> String? = { null },
    /**
     * How to draw one option of a preview-choice row (ME-112). Supplied by the screen,
     * which may name the engine; the rows themselves never learn what a picture is.
     */
    thumbnail: @Composable (EditDocument, Modifier) -> Unit = { _, _ -> }
) {
    val stored = paramsOf(session.panelDocument(document, tool.manifest), tool, picture.sourceSize)
    // **What the panel shows follows the selection; what it writes still starts from the
    // document** (`CanvasTool.displayed`, reported by hand 2026-08-29). The two are not the
    // same question: a control reports the shape under the cursor, and every control sends
    // an absolute value, so reporting the pen instead would make the next click write from
    // a number nobody was looking at.
    val params = (tool as? CanvasTool)?.displayed(stored, selectedId()) ?: stored
    val writer = ParamWriter(session, tool, picture, selectedId)
    // What this tool wants shown for the values it currently holds — all of them, for
    // every tool but Markup.
    val scoped = (tool as? CanvasTool)?.rows(params, picture.sourceSize, selectedId(), picture.timelineMs)
        ?: tool.rows(params, picture.sourceSize)
    val shown = scoped.filterNot { session.clipOpen && it.key in tool.manifest.filmOnly }
    // A spec the tool puts above or under the rows gets no row of its own (ME-174, RD-202):
    // it is *about* the other controls rather than one of them.
    val header = tool.header(shown)
    val footer = tool.footer(shown)
    val visible = shown.filterNot { it in header || it in footer }
    // Air between groups, so a row's name does not sit on the well above it (the author,
    // 2026-11-25: Look's name was all but touching Palette).
    Column(
        verticalArrangement = Arrangement.spacedBy(EditorTokens.Panel.groupGap)
    ) {
        // The tool's own actions about the rows: above them as well buttons, under them as
        // quiet lines (RD-202) — `ParamWell.kt`.
        header.filterIsInstance<ActionParam>().forEach { WellButton(it, writer, accentGlyph = true) }
        groupRuns(visible).forEach { run ->
            key(run.first().key) {
                ParamRun(
                    run = run,
                    params = params,
                    writer = writer,
                    toolId = tool.manifest.id,
                    thumbnail = thumbnail
                )
            }
        }
        footer.filterIsInstance<ActionParam>().forEach { TextAction(it, writer) }
    }
}
