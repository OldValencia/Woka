package com.woka.feature.editor.core

/**
 * Walking between frames, and what the scope switch does to what is on screen (ME-169,
 * reworked 2026-09-02).
 *
 * Extensions on `EditorSession` rather than members of it, because that file is at the
 * size mandate and this is a real seam: everything there is about *an* edit — preview,
 * commit, undo — and everything here is about **which** edit the flows are holding.
 *
 * **The one rule the rest follows.** A frame's document is always the shared layer plus
 * that frame's own work. `All Frames` edits the layer; `Single Frame` edits the frame's
 * half. Everything below is that rule applied to a move, a switch, or a save.
 */

/**
 * Look at another frame, keeping this one's work.
 *
 * In `Single Frame` the frame being left is put aside whole and the frame arrived at is
 * taken back out, recomposed over the layer as it now stands. In `All Frames` there is
 * one document for the file and only the frame index moves.
 *
 * **Undo does not follow.** Each frame keeps its own history, so a step back is a step
 * back through *this* picture; on a frame nobody has touched `canUndo` is false and the
 * button greys, rather than a button that silently edits a picture off screen.
 */
fun EditorSession.moveToFrame(next: Int, scope: EditScope) {
    if (next == frame) return

    // **In All Frames the edit travels with you.** The switch says this edit is for every
    // picture in the file, so walking to another one must go on showing it.
    if (scope == EditScope.AllFrames) {
        base = _document.value
        frame = next
        return
    }

    stashOpenFrame()
    show(elsewhere.value[next]?.rebasedOn(base) ?: firstSightOf(next))
    elsewhere.value -= next
    frame = next
}

/**
 * The switch moved to `Single Frame`: what is on screen becomes the shared layer, and
 * this frame's own work — if it has any put away — comes back on top of it.
 *
 * **The exact inverse of [detachFrame], and it has to be** (reported by hand
 * 2026-09-02). That one puts the open frame's work aside to show the layer alone; this
 * one did not take it back, so switching to `All Frames` and straight back left the work
 * stranded in the stash for a frame the editor was sitting on — invisible on screen, and
 * dropped by [layers] when `Done` came.
 */
fun EditorSession.rebase() {
    base = _document.value
    val own = elsewhere.value[frame] ?: return
    elsewhere.value -= frame
    show(own.rebasedOn(base))
}

/**
 * The switch moved to `All Frames`: the frame's own work is put aside and the **shared
 * layer** is what is shown and edited (reported by hand 2026-09-02).
 *
 * It used to do nothing, so a frame's own work stayed on screen, was declared the base on
 * the next walk, and rode to every other picture in the file. What `All Frames` means is
 * the layer, and the layer is what it has to show.
 */
fun EditorSession.detachFrame() {
    if (_document.value == base) return
    stashOpenFrame()
    show(FrameEdit.opening(base, base))
}

/**
 * What `Done` writes, in the two layers the file keeps (ME-169, reshaped 2026-09-02).
 *
 * **The scope says which layer the open frame is.** `All Frames` means the document on
 * screen *is* the file-wide edit; `Single Frame` means it is that layer plus this frame's
 * own, and the shared half is what the session has held since the switch moved.
 *
 * **Every frame the sitting touched is written either way.** It used to return the open
 * frame alone in `All Frames`, so work stashed before the switch was dropped in silence.
 */
fun EditorSession.layers(scope: EditScope): EditLayers {
    val shared = if (scope == EditScope.AllFrames) _document.value else base
    val stashed = elsewhere.value
        .filterValues { it.isDirty }
        .mapValues { EditDocument(it.value.rebasedOn(shared).ownOps) }
    val open = when (scope) {
        // In All Frames the open frame *is* the shared layer, so it adds nothing.
        EditScope.AllFrames -> emptyMap()
        EditScope.SingleFrame ->
            mapOf(frame to EditDocument(_document.value.ops - shared.ops.toSet()))
    }
    return EditLayers(
        base = shared,
        frames = (stashed + open).filterValues { it.ops.isNotEmpty() },
        touched = elsewhere.value.keys + frame
    )
}

/** What the open frame adds to the shared layer — nothing, for most of them. */
fun EditorSession.ownOpsOfOpenFrame(): List<ToolOp> = _document.value.ops - base.ops.toSet()

/**
 * The open frame's work put away, so something else can be shown in its place.
 *
 * **It refuses to overwrite.** While `All Frames` is showing, this frame's own work is
 * already put away and what is on screen is the layer — stashing *that* would replace the
 * work with the layer and lose it. The guard is cheap and the alternative is a rule that
 * only holds while nothing calls these two in an order nobody has tried yet.
 */
private fun EditorSession.stashOpenFrame() {
    if (elsewhere.value.containsKey(frame)) return
    elsewhere.value += frame to FrameEdit(_document.value, _history.value, openedWith, base)
}

private fun EditorSession.show(edit: FrameEdit) {
    openedWith = edit.openedWith
    _history.value = edit.history
    _document.value = edit.document
}

/**
 * A frame on a first visit: **the shared layer, plus whatever that frame carries of its
 * own**.
 *
 * The frame's own half is `storedFor(next)` minus the file's base, which is why the
 * session is told the base separately: the composed document cannot be taken apart.
 *
 * **`openedWith` is the file's, never the layer's.** It is what `FrameEdit.isDirty`
 * compares against, so setting it to the unsaved layer made a frame carrying real work
 * report clean — and [layers] filters a stashed frame on exactly that.
 */
private fun EditorSession.firstSightOf(next: Int): FrameEdit {
    val stored = storedFor(next)
    val document = EditDocument(base.ops + (stored.ops - storedBase.ops.toSet()))
    return FrameEdit(document, EditorHistory.startingAt(document), stored, base)
}
