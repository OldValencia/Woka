package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.unit.dp
import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.BoolParam
import com.woka.feature.editor.core.ChoiceParam
import com.woka.feature.editor.core.ColorParam
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.EditorSession
import com.woka.feature.editor.core.EditorTool
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.IntParam
import com.woka.feature.editor.core.ObjectParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.PreviewChoiceParam
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ParamRows(
    tool: EditorTool,
    session: EditorSession,
    document: EditDocument,
    /** What the picture measures right now — see `ParamWriter.frame`. */
    frame: IntSize = IntSize.Zero,
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
    val params = paramsOf(document, tool)
    val writer = ParamWriter(session, tool, frame, selectedId)
    // What this tool wants shown for the values it currently holds — all of them, for
    // every tool but Markup.
    val visible = tool.rows(params)
    Column(
        verticalArrangement = Arrangement.spacedBy(EditorTokens.Inspector.rowGap)
    ) {
        // Consecutive actions become one wrapping group rather than one row each.
        // Four buttons stacked vertically, each with an empty label column beside it,
        // is what made the Transform panel a tall strip of white space.
        groupRuns(visible).forEach { run ->
            if (run.size > 1 || run.first() is ActionParam) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    run.forEach { spec ->
                        key(spec.key) { ParamRow(spec, params, writer, tool.manifest.id, thumbnail) }
                    }
                }
            } else {
                val spec = run.first()
                key(spec.key) { ParamRow(spec, params, writer, tool.manifest.id, thumbnail) }
            }
        }
    }
}

/**
 * One spec, one renderer.
 *
 * The `when` is **exhaustive with no `else`**, so a ninth `ParamSpec` subtype stops
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

        // A strip of the picture through each option's own `stage()` (ME-112). The row
        // draws no bitmap itself: it asks the engine, through `thumbnail`, for a picture
        // of a document — which is what keeps the shell free of raster types (§3.3).
        is PreviewChoiceParam<*> -> PreviewChoiceRow(spec, params, writer, toolId, thumbnail)

        // Draws nothing, by design (§3.4). The value belongs to a `CanvasTool`'s
        // overlay, and a spec that cannot be drawn by a generic row says so in its
        // type rather than arriving at a renderer that then has to guess.
        is ObjectParam<*> -> Unit
    }
}

/**
 * Splits a tool's specs into runs of the same *shape*: consecutive actions in one run,
 * everything else alone.
 *
 * An action is a button and buttons sit beside each other; a slider is a labelled row
 * and rows stack. Grouping by adjacency rather than by sorting keeps the tool's
 * declared order, which is the order its inspector is meant to read in (§3.4).
 */
private fun groupRuns(specs: List<ParamSpec<*>>): List<List<ParamSpec<*>>> {
    val runs = mutableListOf<MutableList<ParamSpec<*>>>()
    specs.forEach { spec ->
        val last = runs.lastOrNull()
        if (last != null && last.first() is ActionParam && spec is ActionParam) {
            last += spec
        } else {
            runs += mutableListOf(spec)
        }
    }
    return runs
}
