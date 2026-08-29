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
    thumbnail: @Composable (EditDocument, Modifier) -> Unit,
    /** Whether this run is a line the tool declared, rather than one `groupRuns` found. */
    pairs: Boolean = false
) {
    when {
        // One scrolling line for however many preview specs sit together — one for
        // every tool but Styles, which has an undertone and a look (ME-141).
        run.first() is PreviewChoiceParam<*> ->
            PreviewChoiceStrip(run, params, writer, toolId, thumbnail)

        // A pair the tool asked for: two named controls on one line, buttons then
        // slider (ME-153). Two and no more — a third would need a third label column
        // and the card it is meant to shorten would grow one.
        run.size == 2 && pairs -> PairedRow(run, params, writer)

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
 *
 * **The tool is asked first** (ME-153). A line it declared is taken whole and this
 * function's own rules are not applied inside it — those rules are about shapes that
 * happen to be alike, and a declared pair is about two controls that mean one thing.
 */
internal fun groupRuns(
    specs: List<ParamSpec<*>>,
    declared: List<List<ParamSpec<*>>> = specs.map(::listOf)
): List<List<ParamSpec<*>>> {
    val out = mutableListOf<List<ParamSpec<*>>>()
    // Consecutive lines the tool left alone are handed to the adjacency rules *together*,
    // or a run of actions would be split into one button per line by the walk itself.
    val loose = mutableListOf<ParamSpec<*>>()
    fun flush() {
        if (loose.isNotEmpty()) out += adjacentRuns(loose.toList())
        loose.clear()
    }
    declared.forEach { line ->
        if (line.size > 1) {
            flush()
            out += line
        } else {
            loose += line
        }
    }
    flush()
    return out
}

/** Consecutive specs of the same shape, folded into one run each. */
private fun adjacentRuns(specs: List<ParamSpec<*>>): List<List<ParamSpec<*>>> {
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
