package com.woka.feature.editor.core

import androidx.compose.runtime.Immutable

/**
 * A way out of the editor other than `Done` — one entry of the `Done ▾` menu
 * (`video-editor.md` §4.5, ME-291).
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
    val summary: String
)
