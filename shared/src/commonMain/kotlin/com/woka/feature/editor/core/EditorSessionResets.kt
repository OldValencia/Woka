package com.woka.feature.editor.core

/**
 * The two ways a session is put back, as distinct from the ordinary edits that move it
 * forward.
 *
 * Extensions rather than members because `EditorSession` is at the size mandate and this
 * is a seam: everything left there is about *making* an edit — preview, commit, undo —
 * and both of these are about undoing a whole sitting. Neither holds state; the session
 * still owns all of it.
 */

/**
 * Back to the document the editor opened with, in one undoable step.
 *
 * **Not Revert** (ME-107): this undoes *this sitting*, and the document it
 * returns to is the one the file carries, not an empty one.
 */
fun EditorSession.reset() {
    if (_document.value == openedWith) return
    _document.value = openedWith
    _history.value = _history.value.push(openedWith)
}

/**
 * The file no longer carries anything: document, history and [EditorSession.openedWith]
 * all go back to empty (ME-107).
 *
 * **It writes nothing.** The engine has already removed the recipe by the time this
 * is called — this is the session catching up with the file, which is why it is not
 * undoable: there is nothing on disk left to undo to.
 *
 * **Every frame, and the shared layer too** (reported by hand 2026-09-02). It cleared
 * the open frame alone, which was the whole of the session until ME-169 gave it a stash
 * and a base. An All Frames revert emptied the file and then a walk brought the work
 * back, because it had never left memory.
 *
 * [scope] decides how much: All Frames removed the whole recipe, so nothing survives;
 * Single Frame removed one layer, so the rest of the sitting stands.
 */
fun EditorSession.revertedToOriginal(scope: EditScope = EditScope.AllFrames) {
    if (scope == EditScope.AllFrames) {
        // The whole recipe went, so nothing in memory may outlive it.
        elsewhere.value = emptyMap()
        base = EditDocument()
        openedWith = EditDocument()
        _document.value = EditDocument()
    } else {
        // **Only this frame's layer went.** The file keeps its own, so what this frame
        // now draws is the shared layer — and what it is measured against is what the
        // file still holds, not nothing. Setting it empty made the session read dirty
        // the instant a revert finished (2026-09-02).
        openedWith = storedBase
        _document.value = base
    }
    _history.value = EditorHistory.startingAt(_document.value)
}
