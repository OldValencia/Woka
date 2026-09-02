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
interface EditorTool : EditorToolPanel {

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
     * It returns a subset of [params] — the values are still written, still folded and
     * still saved; only the rows are fewer. A tool that hid a parameter *and* stopped
     * honouring it would be lying to the document.
     *
     * A spec may be returned **re-defaulted for this picture** — same key, same range,
     * same `encode` — which is how markup starts a caption at a size that suits the file
     * instead of at a constant (ME-159). [sourceSize] is the source's own dimensions, not
     * `drawnSize`: a default derived from the folded result would move when a crop landed.
     * What it must never return is a spec with a *different* key or range, which would
     * write a value the document cannot read back.
     */
    fun rows(params: ParamSet, sourceSize: IntSize): List<ParamSpec<*>> = this.params

    /**
     * A chance to rewrite this tool's own parameters when one of them is set, knowing
     * the frame — the escape hatch [stage] deliberately does not have (ME-110).
     *
     * "Long edge 2560" is a scale of `2560 / frame`, and [stage] never sees a frame, so
     * a choice like that could not otherwise become anything. It is resolved **at the
     * moment of the click**, where the shell knows the picture's size, and what lands in
     * the document is the resolved value — the fold stays a pure function of parameters.
     *
     * [stats] is the same escape hatch widened to the **picture** (ME-166): a histogram
     * and a black point, never the pixels. An automatic correction has to read the
     * photograph, and the alternative — letting [stage] sample it — would end the purity
     * that lets one fold serve the preview and the save. Null when the medium has no
     * still to measure, and while the proxy is still being decoded.
     *
     * Null means "nothing to rewrite", which is every tool but one today, and is why
     * this is defaulted rather than added to each of them.
     */
    fun resolve(
        spec: ParamSpec<*>,
        params: ParamSet,
        frame: IntSize,
        stats: SourceStats? = null
    ): ParamSet? = null

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
     * Every spec this tool has, re-defaulted for the picture that is open.
     *
     * The identity for every tool but Markup, which starts a caption at a size the file
     * suggests rather than at a constant (ME-159). A returned spec keeps its key, its
     * range and its encoding — only [ParamSpec.default] moves — because everything
     * downstream finds a value by key.
     *
     * **This, and not [rows], is what [defaults] is built from.** `rows` answers "what is
     * worth drawing", which is a subset; seeding a document from it would drop the
     * parameters the panel happens not to be showing.
     */
    fun specs(sourceSize: IntSize): List<ParamSpec<*>> = params

    /**
     * Every parameter at its starting value — **materialised**, so every key is present.
     *
     * That is exactly why it takes [sourceSize]. A derived default that only applied when
     * a key was *missing* would never apply at all: this seeds them all, and the seed is
     * what the panel reads and what the first operation carries. ME-159 shipped inert
     * once for precisely that reason.
     */
    fun defaults(sourceSize: IntSize = IntSize.Zero): ParamSet =
        ParamSet(specs(sourceSize).associate { it.defaultEntry() })
}

/**
 * The spec's key paired with its encoded default. An extension so the star
 * projection in `List<ParamSpec<*>>` is captured once here, rather than forcing
 * every caller to know that `encode(default)` is type-safe for a `*`.
 */
private fun <T> ParamSpec<T>.defaultEntry(): Pair<String, ParamValue> = key to encode(default)
