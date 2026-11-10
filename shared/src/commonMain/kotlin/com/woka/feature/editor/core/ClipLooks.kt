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
 * [open] is the clip being edited, whose look is already in the document; null with no
 * clip selected, where the document is the shared layer alone.
 *
 * **[shared] is handed in, not worked out from the document** (review 1.0.7, F1): an open
 * clip's own Adjust replaces the film's in the document, so the document less the clip's
 * own work had lost the film's Adjust, and every other clip played without it.
 */
@Stable
class ClipLooks(val open: Int?, val shared: List<ToolOp>, val ownOf: (Int) -> List<ToolOp>) {

    /** What clip [key] draws, given the [document] on screen. */
    fun documentFor(document: EditDocument, key: Int): EditDocument {
        if (key == open) return document
        // A drag in flight belongs to the open clip, so another clip shows it only when
        // what is being dragged is the shared layer itself.
        val pending = document.pending.takeIf { open == null }
        return EditDocument(shared + ownOf(key), pending)
    }
}

/** Null for a still, and for a film the shell has no session for — a test's fake. */
val LocalClipLooks = compositionLocalOf<ClipLooks?> { null }
