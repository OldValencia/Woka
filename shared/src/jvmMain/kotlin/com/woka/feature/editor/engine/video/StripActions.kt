package com.woka.feature.editor.engine.video

import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.editor.tools.trim.delete
import com.woka.feature.editor.tools.trim.duplicate
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
    apply(clips.split(at, newClipId()))
}

/** Removes the selected clip and closes the hole; the selection goes with it. */
internal fun StripState.deleteSelected(id: String) {
    apply(clips.delete(id))
    edits.select(null)
}

/** Puts a copy beside the selected clip and selects the copy, which is what was made. */
internal fun StripState.duplicateSelected(id: String) {
    val copyId = newClipId()
    apply(clips.duplicate(id, copyId))
    edits.select(copyId)
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

/** `MarkupState.newShapeId`'s shape: eight characters, minted once, never reused. */
private fun newClipId(): String = UUID.randomUUID().toString().take(8)
