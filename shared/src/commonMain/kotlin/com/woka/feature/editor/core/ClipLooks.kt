package com.woka.feature.editor.core

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf

/**
 * Each clip's own look, for an engine that draws a film one clip after another (ME-215).
 *
 * `Surface` is handed one document — the one being edited — and a film shows several in
 * turn: the shared layer plus whichever clip is playing. So the shell hands the engine the
 * session's answer per layer key, and the engine composes the clip under the play head.
 *
 * [open] is the clip being edited under This Clip, whose look is already in the document;
 * null under All Clips, where the document is the shared layer alone.
 */
@Stable
class ClipLooks(val open: Int?, val ownOf: (Int) -> List<ToolOp>) {

    /** What clip [key] draws, given the [document] on screen. */
    fun documentFor(document: EditDocument, key: Int): EditDocument {
        if (key == open) return document
        val shared = open?.let { document.ops - ownOf(it).toSet() } ?: document.ops
        return document.copy(ops = shared + ownOf(key))
    }
}

/** Null for a still, and for a film the shell has no session for — a test's fake. */
val LocalClipLooks = compositionLocalOf<ClipLooks?> { null }
