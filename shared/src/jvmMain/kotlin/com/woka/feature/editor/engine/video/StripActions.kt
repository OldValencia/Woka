package com.woka.feature.editor.engine.video

import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.editor.tools.trim.atTimeline
import com.woka.feature.editor.tools.trim.delete
import com.woka.feature.editor.tools.trim.duplicate
import com.woka.feature.editor.tools.trim.freezeAt
import com.woka.feature.editor.tools.trim.join
import com.woka.feature.editor.tools.trim.split
import com.woka.feature.editor.tools.trim.wholeSource
import java.util.UUID

/**
 * The strip's three buttons — ✂ on the play head, and the selected clip's ⌫ and ⧉
 * (ME-212, ME-213).
 *
 * **On the strip, not in the inspector**, against the mockup's inspector row: a split
 * needs the play head and a delete needs the selection, and both live here, in the band.
 * An `ActionParam` would have to reach them through `EditorTool.resolve`, which is handed a
 * frame size and a histogram and — rightly — nothing that moves at sixty frames a second.
 * The mockup already calls the floating bar the shortcut; here it is the whole of it.
 *
 * Each is one history entry, through `StripState.apply`, and does nothing — pushes
 * nothing — when the list would not change: a split on a join, a delete of the last clip.
 */

/** Splits the clip under the play head at the play head's millisecond. */
internal fun StripState.splitAtPlayHead() {
    val at = walk.timelineMs(clips, player.currentPositionMs)
    val before = clips
    val after = before.split(at, newClipId())
    apply(after)
    // The new half keeps the look of the clip it was cut from (ME-215).
    val cut = before.getOrNull(before.atTimelineIndex(at))
    after.firstOrNull { new -> before.none { it.id == new.id } }?.let { new -> cut?.let { edits.copyLayer(it.key, new.key) } }
}

/** Removes the selected clip and closes the hole; the selection goes with it. */
internal fun StripState.deleteSelected(id: String) {
    apply(clips.delete(id))
    edits.select(null)
}

/** Puts a copy beside the selected clip and selects the copy, which is what was made. */
internal fun StripState.duplicateSelected(id: String) {
    val copyId = newClipId()
    val after = clips.duplicate(id, copyId)
    val original = clips.firstOrNull { it.id == id }
    apply(after)
    // The copy carries the original's look too (ME-215).
    after.firstOrNull { it.id == copyId }?.let { copy -> original?.let { edits.copyLayer(it.key, copy.key) } }
    edits.select(copyId)
}

/** Joins the selected clip back to the neighbour it was split from; the first one stays selected. */
internal fun StripState.joinSelected(id: String) {
    val after = clips.join(id)
    if (after == clips) return
    // Joined to the right the clip keeps its id; joined to the left, the left one's survives.
    val kept = after.firstOrNull { it.id == id } ?: clips.getOrNull(clips.indexOfFirst { it.id == id } - 1)
    apply(after)
    edits.select(kept?.id)
}

/**
 * A freeze of the frame under the play head, two seconds long (ME-217) — the ❄ on the play
 * head. The clip it was cut from lends its look to the freeze and to its own new half. **The
 * selection is left alone** (the author, 2026-11-04): what was selected stays selected.
 */
internal fun StripState.freezeAtPlayHead() {
    val at = walk.timelineMs(clips, player.currentPositionMs)
    val source = clips.getOrNull(clips.atTimelineIndex(at))
    val after = clips.freezeAt(at, newClipId(), newClipId())
    if (after == clips) return
    apply(after)
    after.filter { clip -> clips.none { it.id == clip.id } }.forEach { new ->
        source?.let { edits.copyLayer(it.key, new.key) }
    }
}

/** Selects [clip] and opens its layer, under This Clip (ME-215). */
internal fun StripState.focus(clip: TrimClip) {
    edits.select(clip.id)
    edits.focus(clip.key)
}

/**
 * Right-click on the strip: the cut is taken back to the whole film, as one history
 * entry — the editor's right-click-resets rule (`video-editor.md` §4.1b) applied to the
 * one control the schema does not render. An empty list is what `TrimTool` reads as "no
 * cut"; nothing is written when there is no cut to remove.
 */
internal fun StripState.resetCut() {
    if (clips == wholeSource(durationMs)) return
    edits.select(null)
    edits.preview(TrimTool.opFor(emptyList()))
    edits.commit()
}

private fun List<TrimClip>.atTimelineIndex(ms: Long): Int = atTimeline(ms)?.index ?: -1

/** `MarkupState.newShapeId`'s shape: eight characters, minted once, never reused. */
private fun newClipId(): String = UUID.randomUUID().toString().take(8)
