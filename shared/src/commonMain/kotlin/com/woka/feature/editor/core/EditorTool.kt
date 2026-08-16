package com.woka.feature.editor.core

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
