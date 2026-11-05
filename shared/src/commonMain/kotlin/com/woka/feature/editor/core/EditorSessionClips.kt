package com.woka.feature.editor.core

/**
 * A film's clips as the session's frames (ME-215, `video-editor.md` §3.3): **This Clip** is
 * `Single Frame` keyed on a clip's layer key, so a look is a binding and not a mechanism.
 *
 * Its own file because `EditorSessionFrames` is at the size mandate, and because both of
 * these exist only for a timeline: a still's frames are never drawn side by side, and a
 * film's clips are — one after another, each through its own layer.
 */

/**
 * Where a video session stands when no clip is open: on no clip at all.
 *
 * A film opens in `All Clips`, editing the shared layer. Standing on clip 0 there would make
 * the first `This Clip` on clip 0 a move to the frame the session is already on — which
 * returns early, and the clip's stored look would never be read back.
 */
const val NO_CLIP = -1

/**
 * What clip [key] adds to the shared layer, as the session holds it now: its stashed work,
 * the open document's delta when it is the clip being edited, or what the file carries.
 */
fun EditorSession.ownOpsOf(key: Int): List<ToolOp> {
    elsewhere.value[key]?.let { return it.ownOps }
    if (key == frame) return _document.value.ops - base.ops.toSet()
    return storedFor(key).ops - storedBase.ops.toSet()
}

/**
 * Gives clip [to] the look clip [from] has — a split's right half, a duplicate (ME-215).
 *
 * Called from `All Clips`, the only scope the strip cuts in, so the document on screen is
 * the shared layer and the copy is composed over it. Stashed as a frame the sitting
 * touched, so `layers` writes it and `Done` keeps it.
 *
 * **Even no look is copied over a stale one.** A new clip's key is the list's largest plus one,
 * so it can be the key of a clip deleted or joined away whose look is still stashed or stored
 * — and that look came back on the new clip (1.0.6 review). Nothing at all only when neither
 * side has one.
 */
fun EditorSession.copyFrame(from: Int, to: Int) {
    val own = ownOpsOf(from)
    if (own.isEmpty() && ownOpsOf(to).isEmpty()) return
    elsewhere.value += to to layerWith(to, own)
}

/**
 * Pastes [ops] onto clip [to] (ME-267): of the kinds [taken] picks — the picture, the
 * sound, or both — the clip's own are **replaced** by [ops]', the rest of its own kept.
 *
 * From `All Clips`, like [copyFrame], so the document on screen does not change; the step
 * is a history entry carrying the layer's before and after, and undo puts it back.
 */
fun EditorSession.pasteOnClip(to: Int, ops: List<ToolOp>, taken: (ToolOp) -> Boolean) {
    val own = ownOpsOf(to)
    val pasted = own.filterNot(taken) + ops.filter(taken)
    if (pasted == own) return
    // The clip open on a film (ME-221) is on screen, not put away.
    if (film.linear && to == frame) {
        _document.value = EditDocument(base.ops + pasted)
        return record(_document.value)
    }
    val before = elsewhere.value[to]
    val after = layerWith(to, pasted)
    elsewhere.value += to to after
    record(_document.value, LayerChange(to, before, after))
}

/** Clip [key] holding [own] over the shared layer, put away as a touched frame. */
private fun EditorSession.layerWith(key: Int, own: List<ToolOp>): FrameEdit {
    val shared = sharedLayer
    val document = EditDocument(shared.ops + own)
    return FrameEdit(document, EditorHistory.startingAt(document), storedFor(key), shared)
}

/**
 * The off-screen half of an undo or a redo from [from] to [to] (ME-267): the entry being
 * left backwards puts its layer back as it was; the entry being reached forwards, as it
 * became. Nothing for a step that changed only the document on screen.
 */
internal fun EditorSession.stepLayers(from: EditorHistory, to: EditorHistory) {
    val step = when {
        to.index < from.index -> from.changes.getOrNull(from.index)?.let { it.key to it.before }
        to.index > from.index -> to.changes.getOrNull(to.index)?.let { it.key to it.after }
        else -> null
    } ?: return
    val (key, layer) = step
    elsewhere.value = if (layer == null) elsewhere.value - key else elsewhere.value + (key to layer)
}
