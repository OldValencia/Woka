package com.woka.feature.editor.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.unit.dp
import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.EditDocument
import com.woka.feature.editor.core.PadParam
import com.woka.feature.editor.core.ParamSet
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.PreviewChoiceParam
import androidx.compose.ui.Modifier

/**
 * How a tool's specs are grouped into what the panel actually lays out, split from
 * [ParamRows] under the size mandate when preview strips learned to share a line.
 *
 * **Grouping is by adjacency, never by sorting.** A tool's declared order is the
 * order its inspector is meant to read in (§3.4), so a run is a stretch of
 * neighbours of the same shape and nothing is moved to join one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ParamRun(
    run: List<ParamSpec<*>>,
    params: ParamSet,
    writer: ParamWriter,
    toolId: String,
    thumbnail: @Composable (EditDocument, Modifier) -> Unit
) {
    when {
        // One scrolling line for however many preview specs sit together — one for
        // every tool but Styles, which has an undertone and a look (ME-141).
        run.first() is PreviewChoiceParam<*> ->
            PreviewChoiceStrip(run, params, writer, toolId, thumbnail)

        // Consecutive actions become one wrapping group rather than one row each.
        // Four buttons stacked vertically, each with an empty label column beside it,
        // is what made the Transform panel a tall strip of white space.
        run.size > 1 || run.first() is ActionParam ->
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                run.forEach { spec ->
                    key(spec.key) { ParamRow(spec, params, writer, toolId, thumbnail) }
                }
            }

        else -> run.first().let { spec ->
            key(spec.key) { ParamRow(spec, params, writer, toolId, thumbnail) }
        }
    }
}

/**
 * Splits a tool's specs into runs of the same *shape*: consecutive actions in one
 * run, consecutive preview strips in another, everything else alone.
 *
 * An action is a button and buttons sit beside each other; a preview strip is a line
 * of squares and two of them read as one line; a slider is a labelled row and rows
 * stack.
 */
internal fun groupRuns(specs: List<ParamSpec<*>>): List<List<ParamSpec<*>>> {
    val runs = mutableListOf<MutableList<ParamSpec<*>>>()
    specs.forEach { spec ->
        val last = runs.lastOrNull()
        if (last != null && joins(last.first(), spec)) last += spec else runs += mutableListOf(spec)
    }
    return runs
}

/**
 * Whether [next] belongs to the run [head] started.
 *
 * A pad joins a preview run rather than starting a row of its own: it is drawn as a
 * small indicator at the end of the line and opens into a popover, so it is part of
 * that line's furniture (2026-08-28). A pad with no strip before it still stands
 * alone, which is what a tool declaring only a pad would get.
 */
private fun joins(head: ParamSpec<*>, next: ParamSpec<*>): Boolean =
    (head is ActionParam && next is ActionParam) ||
        (head is PreviewChoiceParam<*> && (next is PreviewChoiceParam<*> || next is PadParam))
