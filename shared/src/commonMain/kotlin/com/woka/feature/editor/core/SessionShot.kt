package com.woka.feature.editor.core

import androidx.compose.runtime.Immutable

/**
 * The whole of a film session's state at one history step (ME-221): what is on screen, the
 * shared layer under it, which clip is open, every clip put away, what the open one is
 * measured against, and which clip the strip has selected.
 *
 * **A film keeps one line of history**, selections included, so a step back has to put back
 * all of this at once — the per-frame histories a GIF walks between cannot say that leaving a
 * clip was a step. Snapshots for `EditorHistory`'s reason: restoring one cannot get an
 * inverse wrong.
 */
@Immutable
data class SessionShot(
    val document: EditDocument,
    val base: EditDocument,
    val frame: Int,
    val elsewhere: Map<Int, FrameEdit>,
    val openedWith: EditDocument,
    val selection: String?
)
