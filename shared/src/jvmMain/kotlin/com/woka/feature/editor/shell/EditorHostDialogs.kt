package com.woka.feature.editor.shell

import androidx.compose.runtime.Composable
import com.woka.feature.editor.core.EditScope

/**
 * The two questions the editor asks before it throws something away: a revert, and a
 * close with unsaved work.
 *
 * **It was three until ME-169.** A frame change asked as well, because the host rebuilt
 * the session and the work went with it; the session now keeps a document and a history
 * per frame, so there is nothing to lose and nothing to ask. They are one concern: each
 * is a boolean the host holds, a dialog, and a confirmed action, and none of them
 * touches the session, the engine or the canvas.
 *
 * **At most one is ever open**, because each is raised by a control the others hide
 * behind, which is why a single [onKeepEditing] clearing all three is honest rather
 * than lax.
 *
 * A fourth question — the window's close button, ME-160 — is deliberately **not** here.
 * It is raised from outside the editor rather than by a control inside it, so it can
 * coexist with one of these three, and `EditorHost` had no lines left to hold its
 * state: `EditorExitEffect` owns the question, the answer and the dialog together.
 */
@Composable
internal fun EditorHostDialogs(
    askingToRevert: Boolean,
    /** Which Revert the switch is pointing at, for the dialog to say so (ME-176). */
    revertScope: EditScope,
    onRevert: () -> Unit,
    askingToDiscard: Boolean,
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
    /**
     * `To SDR` is about to rewrite the photograph's pixels (ME-118).
     *
     * Defaulted, so a caller that cannot raise this question — the frame-discard tests
     * exercise the other two — says nothing rather than passing a pair of blanks.
     */
    askingToBake: Boolean = false,
    onBake: () -> Unit = {}
) {
    if (askingToRevert) {
        RevertDialog(onRevert = onRevert, onKeepEditing = onKeepEditing, scope = revertScope)
    }

    if (askingToDiscard) {
        DiscardChangesDialog(onDiscard = onDiscard, onKeepEditing = onKeepEditing)
    }

    if (askingToBake) {
        BakeToSdrDialog(onBake = onBake, onKeepEditing = onKeepEditing)
    }
}
