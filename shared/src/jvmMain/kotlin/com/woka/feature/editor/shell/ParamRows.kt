package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.BoolParam
import com.woka.feature.editor.core.CanvasTool
import com.woka.feature.editor.core.ChoiceParam
import com.woka.feature.editor.core.ColorParam
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.IntParam
import com.woka.feature.editor.core.ObjectParam
import com.woka.feature.editor.core.PadParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.PreviewChoiceParam
import com.woka.feature.editor.core.ToggleSetParam
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize

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
    val stored = paramsOf(document, tool, picture.sourceSize)
    // **What the panel shows follows the selection; what it writes still starts from the
    // document** (`CanvasTool.displayed`, reported by hand 2026-08-29). The two are not the
    // same question: a control reports the shape under the cursor, and every control sends
    // an absolute value, so reporting the pen instead would make the next click write from
    // a number nobody was looking at.
    val params = (tool as? CanvasTool)?.displayed(stored, selectedId()) ?: stored
    val writer = ParamWriter(session, tool, picture, selectedId)
    // What this tool wants shown for the values it currently holds — all of them, for
    // every tool but Markup.
    val shown = tool.rows(params, picture.sourceSize)
    // A spec that rides inside another spec's popover gets **no control of its own**
    // (ME-155). `rows` still names it, and must: it is still written, still folded and
    // still saved — only the place it is reached from has moved.
    val ridden = shown.flatMap { tool.folded(it, shown) }.toSet()
    val visible = shown.filterNot { it in ridden }
    // Which of those the tool draws as one compact bar — none, for every tool but Markup.
    val compact = tool.lines(visible)
    Column(
        verticalArrangement = Arrangement.spacedBy(EditorTokens.Inspector.rowGap)
    ) {
        groupRuns(visible, compact).forEach { run ->
            key(run.first().key) {
                ParamRun(
                    run = run,
                    params = params,
                    writer = writer,
                    toolId = tool.manifest.id,
                    thumbnail = thumbnail,
                    compact = run in compact,
                    folded = { spec -> tool.folded(spec, shown) }
                )
            }
        }
    }
}

/**
 * One spec, one renderer.
 *
 * The `when` is **exhaustive with no `else`**, so a tenth `ParamSpec` subtype stops
 * this file compiling until it has a row. That is the same guard `EditorFixtureTest`
 * applies from the other side, and between them a spec type cannot reach a document
 * without a way to edit it.
 */
@Composable
internal fun ParamRow(
    spec: ParamSpec<*>,
    params: ParamSet,
    writer: ParamWriter,
    toolId: String = "",
    thumbnail: @Composable (EditDocument, Modifier) -> Unit = { _, _ -> }
) {
    when (spec) {
        is FloatParam -> FloatParamRow(spec, params, writer)
        is IntParam -> IntParamRow(spec, params, writer)
        is BoolParam -> BoolParamRow(spec, params, writer)
        is ChoiceParam<*> -> ChoiceParamRow(spec, params, writer)
        is ColorParam -> ColorParamRow(spec, params, writer)
        is ActionParam -> ActionParamRow(spec, writer)

        // The same chips as a choice row above four options, lighting any number of them
        // (ME-147) — a set is what a choice cannot be, not a wider kind of one.
        is ToggleSetParam<*> -> ToggleSetParamRow(spec, params, writer)

        // Two numbers from one gesture (ME-141) — which is why it is one spec.
        is PadParam -> PadParamRow(spec, params, writer)

        // A strip of the picture through each option's own `stage()` (ME-112). The row
        // draws no bitmap itself: it asks the engine, through `thumbnail`, for a picture
        // of a document — which is what keeps the shell free of raster types (§3.3).
        //
        // A run of one: `ParamRun` sends neighbouring preview specs here together, and
        // this branch is what a caller reaching for a single spec gets (ME-141).
        is PreviewChoiceParam<*> ->
            PreviewChoiceStrip(listOf(spec), params, writer, toolId, thumbnail)

        // Draws nothing, by design (§3.4). The value belongs to a `CanvasTool`'s
        // overlay, and a spec that cannot be drawn by a generic row says so in its
        // type rather than arriving at a renderer that then has to guess.
        is ObjectParam<*> -> Unit
    }
}

