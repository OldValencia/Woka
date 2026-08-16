package com.woka.feature.editor.core

/**
 * Where a save writes. The default is never the source file (§5).
 *
 * Declared here in ME-008 because [EditorEngine.save] names it; ME-009 owns the
 * naming rules that produce [Copy.name] and the atomic write underneath both.
 */
sealed interface SaveTarget {

    /**
     * A new file beside the original — `photo-edited.jpg`. The default everywhere:
     * the original is preserved by not being written to, which is the whole of
     * Woka's non-destructive guarantee. There is no backup store because there
     * does not need to be one.
     */
    data class Copy(val name: String) : SaveTarget

    /**
     * The source file itself.
     *
     * **Only ever constructed by an explicit user action**, behind a confirmation
     * that says plainly that no copy is kept and the original cannot be recovered
     * (§5). Nothing may default to this, and no code path may reach it because a
     * copy failed.
     */
    data object Overwrite : SaveTarget
}
