package com.woka.feature.editor.core

/**
 * How a tool's controls are **laid out** — as distinct from what the tool is and what it
 * performs, which is [EditorTool] and extends this.
 *
 * Split out 2026-09-02 when that file passed the size mandate, and it is a real seam
 * rather than a cut through the middle of one: everything here answers "what shape do
 * this tool's own rows take", every default is the plain answer, and a tool that does not
 * care — which is most of them — overrides nothing at all.
 *
 * §3.4's rule is untouched. A tool still never knows *where* it sits, in what order it
 * applies, or how its panel is spaced; these say only which of its specs are shown and
 * which of them are one idea.
 */
interface EditorToolPanel {

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
}
