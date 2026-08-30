package com.woka.feature.editor.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The half of the engine seam that **draws** — split out of [EditorEngine] when
 * ME-156 pushed that file past the size mandate.
 *
 * The cut is along the one line already in the interface: everything else there
 * *answers* a question about the medium and can be called from a coroutine or a
 * test, while these three are `@Composable` or are raised by the canvas. Nothing
 * else moved with them, and [EditorEngine] extends this, so no implementation and
 * no call site changes — a fake that overrides `Surface` still does.
 *
 * **It still names no bitmap, player or recipe** (§3.3). That constraint is the
 * whole reason the seam exists, and splitting it must not be an opportunity to
 * relax it.
 */
interface EditorEngineSurface {

    /** The live canvas for this medium, drawing [document] as it stands. */
    @Composable
    fun Surface(document: EditDocument, modifier: Modifier)

    /**
     * The same picture, small — one option of a preview-choice row (ME-112).
     *
     * **A thumbnail is a picture, and the shell may not name one** (§3.3). The row asks
     * for one per option, each with a document holding only that option's operation, and
     * never learns what comes back. Defaulted to drawing nothing, so a medium with
     * nothing to preview is unaffected.
     *
     * Implementations must reuse whatever [Surface] already holds: a decode per
     * thumbnail per recomposition is eight decodes a frame.
     */
    @Composable
    fun Thumbnail(document: EditDocument, modifier: Modifier) = Unit

    /**
     * "Start or stop whatever is moving on the canvas", raised by a click on it.
     *
     * Media-agnostic on purpose: an animated still runs a frame clock and the video
     * editor (5b) will run a player, and the shell asks the same question of both
     * without learning what either is. Defaulted to nothing, which is the honest
     * answer for a photograph — the click still happens and nothing moves.
     */
    fun togglePlayback() = Unit
}
