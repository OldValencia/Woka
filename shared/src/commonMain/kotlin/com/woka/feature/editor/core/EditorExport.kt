package com.woka.feature.editor.core

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntSize

/**
 * A way out of the editor other than `Done` — `Create a copy…`, `Save frame as photo…`, or
 * a `RenderOnly` file's `Done` (`video-editor.md` §4.5, ME-291, RD-401).
 *
 * **Named by the engine, run by the engine, and nothing else about it is the shell's.**
 * The shell draws [title] and [summary] and hands the chosen one back; what "render" means
 * — a decoder, an encoder, a container — is the medium's business and the shell may not
 * name it (§3.3). A still offers none, and its `Done` stays the plain button it was.
 *
 * [summary] says what the entry costs, in the mockup's words: a render is a new file
 * that plays anywhere, and `Done` beside it is instant and visible only in Woka. A menu
 * entry that needed a parenthetical to be understood is what the author rejected.
 */
@Immutable
data class EditorExport(
    val id: String,
    val title: String,
    val summary: String,
    /**
     * Where the play head stood when the entry was chosen (ME-294), stamped by the shell
     * before the editor closes — the player goes with it. Null for a medium with no time.
     */
    val atMs: Long? = null,
    /**
     * The size `Create a copy` chose (RD-401), in pixels of the result, stamped by the shell;
     * null for the full frame. **It replaces the document's Resize** — a copy's size is the
     * copy's own, and what `Done` saved does not size it.
     */
    val size: IntSize? = null,
    /** The progress toast's words while it runs, without the ellipsis. */
    val progress: String = "Creating copy",
    /**
     * A still of the moment under the play head rather than the whole edit — the timeline's
     * `Save frame as photo…`, not `Create a copy`'s. The shell finds its doors by this.
     */
    val frame: Boolean = false
)
