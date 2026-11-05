package com.woka.feature.editor.core

/**
 * The operations that **make** an edit: the one in flight, and the one committed.
 *
 * Undo and redo stayed on the session: they navigate a history it owns, and pulling them
 * out only made every caller import them.
 *
 * Extensions on `EditorSession` beside `EditorSessionFrames` and `EditorSessionResets`,
 * for the reason those two are: the session holds the state and owns its lifetime, and
 * this file holds what is done to it. Split off 2026-09-02 when the frame work took that
 * file past the size mandate.
 */

/**
 * Replaces the operation in flight. Pushes **no** history entry, so a slider
 * dragged across its range costs one undo step rather than two hundred (§3.1).
 */
fun EditorSession.preview(op: ToolOp) {
    _document.value = _document.value.withPending(op)
}

/**
 * Folds the operation in flight into the document and records one undo step.
 *
 * On a film with a clip open (ME-221), a shared tool's op — a cut — goes into the shared
 * layer and the clip's own work is composed over it again: a split made with clip 2 selected
 * is the whole film's split, not clip 2's.
 */
fun EditorSession.commit(cardinality: ToolCardinality) {
    val pending = _document.value.pending
    val committed = if (film.linear && frame != NO_CLIP && pending != null && pending.toolId in film.sharedTools) {
        val own = _document.value.ops - base.ops.toSet()
        base = base.withPending(pending).commitPending(cardinality)
        EditDocument(base.ops + own)
    } else {
        _document.value.commitPending(cardinality)
    }
    if (committed == _document.value) return
    _document.value = committed
    record(committed)
}

/** Abandons the operation in flight — an aborted drag, not an undo. */
fun EditorSession.cancelPending() {
    _document.value = _document.value.clearPending()
}
