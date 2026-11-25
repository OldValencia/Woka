package com.woka.feature.editor.shell

import com.woka.feature.editor.core.ActionParam
import com.woka.feature.editor.core.FloatParam
import com.woka.feature.editor.core.PadParam
import com.woka.feature.editor.core.ParamSpec
import com.woka.feature.editor.core.PreviewChoiceParam

/**
 * Which specs are laid out together — the half of [ParamRun] that decides, split from
 * the half that draws when ME-161 added a third shape a run can take.
 *
 * Nothing here composes. That is the seam: these are pure functions over a spec list and
 * are testable without a window, which is what `ParamRunGroupingTest` relies on.
 */

/**
 * Splits a tool's specs into runs of the same *shape*: consecutive actions in one run,
 * consecutive preview rows in another, levels and pads in a well, everything else alone.
 *
 * Grouping is by adjacency, never by sorting: a tool's declared order is the order its
 * inspector reads in (§3.4).
 */
internal fun groupRuns(specs: List<ParamSpec<*>>): List<List<ParamSpec<*>>> = adjacentRuns(specs)

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
        (head is PreviewChoiceParam<*> && next is PreviewChoiceParam<*>) ||
        (head is FloatParam && (next is FloatParam || next is PadParam))
