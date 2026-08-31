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
 * Splits a tool's specs into runs of the same *shape*: consecutive actions in one
 * run, consecutive preview strips in another, everything else alone.
 *
 * An action is a button and buttons sit beside each other; a preview strip is a line
 * of squares and two of them read as one line; a slider is a labelled row and rows
 * stack.
 *
 * **The tool is asked first** (ME-155). A [compact] line it declared is emitted whole at
 * the position of its first spec, and the adjacency rules below are not applied inside it
 * — those rules are about shapes that happen to be alike, and a compact line is about
 * controls the tool has said belong to one bar.
 */
internal fun groupRuns(
    specs: List<ParamSpec<*>>,
    compact: List<List<ParamSpec<*>>> = emptyList()
): List<List<ParamSpec<*>>> {
    val leads = compact.associateBy { it.first() }
    val claimed = compact.flatten().toSet()
    val out = mutableListOf<List<ParamSpec<*>>>()
    // Specs the tool left alone are handed to the adjacency rules *together*, or a run of
    // actions would be split into one button per line by the walk itself.
    val loose = mutableListOf<ParamSpec<*>>()
    fun flush() {
        if (loose.isNotEmpty()) out += adjacentRuns(loose.toList())
        loose.clear()
    }
    specs.forEach { spec ->
        val line = leads[spec]
        when {
            line != null -> {
                flush()
                out += line
            }
            // Already emitted as part of the line its first spec led.
            spec in claimed -> Unit
            else -> loose += spec
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
        (head is PreviewChoiceParam<*> && (next is PreviewChoiceParam<*> || next is PadParam)) ||
        (head is FloatParam && next is FloatParam && head.range == next.range)

/**
 * Whether a run of like sliders is long enough to become a chooser (ME-161).
 *
 * **Four, and the number is a measurement rather than a taste.** A labelled row is 36 dp
 * and the chooser's own furniture — a strip of chips over a slider, a field and `Reset` —
 * is about three rows' worth, so below four the strip trades a click for nothing. At four
 * it breaks even and from there it stops growing at all, which is the point: fifteen
 * adjustments cost the same 115 dp as six.
 *
 * **Derived from the schema, not declared by the tool**, like every other rule in this
 * file. §3.4's claim is that a tool contributes a schema and gets its layout; a
 * `strips()` hook beside `lines()` would have made Adjust ask for a shape instead. Checked
 * 2026-09-01: Adjust is the only tool this catches — Filter declares two floats, Resize
 * one, and Markup's are claimed by the compact line it declares.
 */
internal fun isStrip(run: List<ParamSpec<*>>): Boolean =
    run.size >= STRIP_MIN && run.first() is FloatParam

private const val STRIP_MIN = 4
