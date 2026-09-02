package com.woka.feature.editor.core

/**
 * One frame's editing state, whole: what is drawn, how to walk back through it, what it
 * looked like when the editor reached it, and **which shared layer it was drawn over**
 * (ME-169, corrected 2026-09-02).
 *
 * **The three travel together or not at all.** A document without its history is a
 * picture you cannot undo; a history without the document it belongs to walks somebody
 * else's edit. Keeping them apart is what made a frame change lose work.
 *
 * [over] is the fourth because a stashed frame goes stale otherwise. What is stored is
 * the composed picture — base plus this frame's own — and the base can move while the
 * frame is put away: an `All Frames` edit made afterwards belongs to this frame too.
 * Remembering what it was composed over is what lets [ownOps] recover the frame's own
 * half and compose it again against whatever the base has become.
 */
data class FrameEdit(
    val document: EditDocument,
    val history: EditorHistory,
    val openedWith: EditDocument,
    /** The shared layer this frame's document was composed over when it was stashed. */
    val over: EditDocument = EditDocument()
) {

    /** Whether anything would be lost by leaving this frame as it stands. */
    val isDirty: Boolean get() = document != openedWith

    /** This frame's own half — what it adds to the layer it was composed over. */
    val ownOps: List<ToolOp> get() = document.ops - over.ops.toSet()

    /**
     * This frame, recomposed over a base that may have moved since it was put away.
     *
     * The history is left as it was: it is a record of what was *done* to this frame, and
     * a shared edit made elsewhere is not a step in it.
     */
    fun rebasedOn(base: EditDocument): FrameEdit {
        if (base == over) return this
        val recomposed = EditDocument(base.ops + ownOps)
        return copy(document = recomposed, over = base)
    }

    companion object {

        /**
         * A frame as the editor first reaches it: drawing what the file says, with a
         * history that opens on the same thing.
         *
         * `EditorHistory.startingAt` replays a stored recipe one op per entry, so Undo
         * walks back through earlier sittings and reaches the photograph at the end of
         * them (ME-134) — that is true per frame now, not once per file.
         */
        fun opening(stored: EditDocument, over: EditDocument = EditDocument()): FrameEdit =
            FrameEdit(stored, EditorHistory.startingAt(stored), stored, over)
    }
}
