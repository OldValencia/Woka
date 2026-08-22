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
