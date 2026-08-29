package com.woka.feature.editor.core

import androidx.compose.ui.unit.IntSize

/**
 * One editing element: what it is, what it edits, what it performs (§3.4).
 *
 * Named `EditorTool` rather than `Plugin` on purpose — nothing is loaded from a
 * jar at runtime, and calling it a plugin would promise that. What it does share
 * with a plugin architecture is that the shell derives everything from
 * [manifest] and [params]: a tool writes no panel, no rail entry, no shortcut
 * registration and no settings row.
 *
 * [stage] is **pure** — no Compose, no IO, no cache. It maps parameters to a
 * description of the work, and the engine performs it. That is what lets the fold
 * run at screen scale for the preview and at native scale on save from the same
 * declaration, and what makes a tool testable without a window.
 *
 * Returning null means "this configuration is a no-op" — a rotation of 0°, an
 * adjustment left at its detent — and the fold skips it rather than composing an
 * identity matrix it would then have to recognise.
 */
interface EditorTool {

    val manifest: ToolManifest

    /** The parameters, in the order the inspector draws them. */
    val params: List<ParamSpec<*>>

    fun stage(params: ParamSet): ToolStage?

    /**
     * Which of [params] the inspector draws **right now**, given what they currently say.
     *
     * Defaulted to all of them, which is every tool but Markup: the schema is the UI and a
     * row that appears and vanishes is not a rule the shell can derive. What it *can* do is
     * ask the tool, which is the one place that knows a stroke width means nothing to a
     * caption and a font size means nothing to an arrow.
     *
     * It returns a subset of [params] and never a different spec — the values are still
     * written, still folded and still saved; only the rows are fewer. A tool that hid a
     * parameter *and* stopped honouring it would be lying to the document.
     */
    fun rows(params: ParamSet): List<ParamSpec<*>> = this.params

    /**
     * Whether this tool's inspector may grow to the **dock's** width rather than
     * stopping at the card's usual ceiling (2026-08-28).
     *
     * False for every tool but Styles, and deliberately a declaration rather than
     * something the shell infers: a wide card is only worth it where the content is a
     * line that can use any width, and widening the rest put Markup's swatches and
     * Transform's four buttons in a field of empty space — the defect a fixed 420 dp
     * caused once already, in the other direction.
     */
    val wideInspector: Boolean get() = false

    /**
     * Which of the visible [rows] this tool draws as a **compact line** — labelless
     * controls side by side, each collapsed into a button that opens its own popover
     * (ME-155). Empty for every tool but Markup, and whatever is not claimed here falls
     * to the shell's own grouping untouched.
     *
     * Re-pointed from ME-153's "two controls share a line", which was refused on screen:
     * buttons beside a slider do not work, so the answer is to shorten the controls
     * rather than to merge the rows.
     *
     * **A function of the rows, not a static list.** Markup's rows come and go with the
     * pen, so a fixed line would name a control that is not being shown.
     */
    fun lines(rows: List<ParamSpec<*>>): List<List<ParamSpec<*>>> = emptyList()

    /**
     * Specs that ride inside [spec]'s own popover instead of taking a control of their
     * own — `Corner` inside `Fill`, and nothing else today (ME-155).
     *
     * The plate's roundness has no meaning without the plate, so it belongs where the
     * plate is set. The shell drops whatever is named here from the line it was going to
     * draw, which is why this is asked of the tool rather than inferred: only the tool
     * knows that two of its specs are one idea.
     */
    fun folded(spec: ParamSpec<*>, rows: List<ParamSpec<*>>): List<ParamSpec<*>> = emptyList()

    /**
     * A chance to rewrite this tool's own parameters when one of them is set, knowing
     * the frame — the escape hatch [stage] deliberately does not have (ME-110).
     *
     * "Long edge 2560" is a scale of `2560 / frame`, and [stage] never sees a frame, so
     * a choice like that could not otherwise become anything. It is resolved **at the
     * moment of the click**, where the shell knows the picture's size, and what lands in
     * the document is the resolved value — the fold stays a pure function of parameters.
     *
     * Null means "nothing to rewrite", which is every tool but one today, and is why
     * this is defaulted rather than added to each of them.
     */
    fun resolve(spec: ParamSpec<*>, params: ParamSet, frame: IntSize): ParamSet? = null

    /**
     * One line the inspector prints under this tool's rows — `4000 × 3000 → 2000 × 1500`
     * for a resize (ME-110), nothing for everything else.
     *
     * The frame is passed rather than learned, for the same reason [resolve] takes one:
     * it is a fact about the picture at this moment, not about the tool, and [stage]
     * must go on being a pure function of parameters.
     */
    fun footnote(params: ParamSet, frame: IntSize): String? = null

    /**
     * Every parameter at its declared default.
     *
     * Derived from [params] so a tool never writes its defaults twice — the
     * second copy is the one that drifts, and it drifts silently because both
     * spellings compile.
     */
    fun defaults(): ParamSet = ParamSet(params.associate { it.defaultEntry() })
}

/**
 * The spec's key paired with its encoded default. An extension so the star
 * projection in `List<ParamSpec<*>>` is captured once here, rather than forcing
 * every caller to know that `encode(default)` is type-safe for a `*`.
 */
private fun <T> ParamSpec<T>.defaultEntry(): Pair<String, ParamValue> = key to encode(default)
