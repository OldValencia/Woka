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
 * touched, so `layers` writes it and `Done` keeps it; nothing at all when [from] has no look.
 */
fun EditorSession.copyFrame(from: Int, to: Int) {
    val own = ownOpsOf(from)
    if (own.isEmpty()) return
    val shared = _document.value
    val document = EditDocument(shared.ops + own)
    elsewhere.value += to to FrameEdit(document, EditorHistory.startingAt(document), storedFor(to), shared)
}
