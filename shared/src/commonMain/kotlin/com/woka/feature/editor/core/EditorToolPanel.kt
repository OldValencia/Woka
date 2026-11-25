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
     * Specs drawn **above** the rows as full-width buttons rather than as rows of their
     * own (ME-174; RD-202 moved them from the card's top right — Adjust's Auto-enhance).
     *
     * The same shape as [folded] and for the same reason: only the tool knows that one of
     * its controls is *about* the others rather than one of them. Adjust's Auto and Reset
     * All set the twelve sliders, so standing in the stack with them they read as a
     * thirteenth and fourteenth adjustment.
     *
     * The shell drops whatever is named here from the rows, exactly as it drops [folded].
     * Empty for every tool but Adjust — this is a declaration, never a rule about
     * `ActionParam`: Transform's four actions are the tool, and stay rows.
     */
    fun header(rows: List<ParamSpec<*>>): List<ParamSpec<*>> = emptyList()

    /**
     * Specs drawn **under** the rows as quiet text actions (RD-202): Adjust's Reset
     * adjustments, done rarely and on purpose. Dropped from the rows like [header].
     */
    fun footer(rows: List<ParamSpec<*>>): List<ParamSpec<*>> = emptyList()

    /**
     * How the picture moved when [op] was just added, for the canvas to show the movement
     * rather than jump to the result (RD-203): Transform's turns and flips. Null for every
     * operation that does not move the picture as a whole.
     */
    fun canvasMotion(op: ToolOp): CanvasMotion? = null
}
