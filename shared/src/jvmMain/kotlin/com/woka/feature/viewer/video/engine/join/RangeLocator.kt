package com.woka.feature.viewer.video.engine.join

import androidx.compose.runtime.snapshots.Snapshot
import com.woka.core.ui.image.SourceRange
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.seekInRanges
import com.woka.feature.viewer.video.engine.setClipPace
import com.woka.feature.viewer.video.engine.showRange

/**
 * Where in the kept ranges a source position is (ME-232) — after a seek, or after an edit
 * of the list moved the ranges under the play head. Split out of `JoinStep` for the size
 * mandate; called from the decode loop's thread.
 *
 * The range decoding wins if it holds [atMs], since a duplicated clip puts the same source
 * in two. A position in no range moves to the start of the next one; past the last, the
 * decoder stays in the last, whose end then stops the picture.
 */
internal fun relocate(player: VideoPlayerState, atMs: Long) {
    val joins = player.edit.joins
    val ranges = joins.ranges
    // An edit of the list may have left the index past its end.
    joins.decoding = joins.decoding.coerceIn(0, ranges.lastIndex)
    val held = ranges.getOrNull(joins.decoding)
    // Still the same range, but the edit may have changed its speed: without this a pace set
    // on the clip playing waited for the next seek (reported 2026-11-20). Unchanged, a no-op.
    if (held?.holds(atMs) == true) return Snapshot.withMutableSnapshot { player.setClipPace(held.speed, joins) }
    val inside = ranges.indexOfFirst { it.holds(atMs) }
    // **Past the decoding range's end, "next" is the next in the list**, not the next in the
    // source: with the clips out of order the source's next was an earlier clip, and the end
    // of the film jumped back into it (1.0.6 review). Past the last one there is none.
    val current = ranges[joins.decoding]
    val next = if (!current.isFreeze && atMs >= current.toMs) {
        (joins.decoding + 1).takeIf { it <= ranges.lastIndex } ?: -1
    } else {
        ranges.indexOfFirst { it.fromMs > atMs }
    }
    if (inside < 0 && next >= 0) return seekToStart(player, next)
    joins.decoding = if (inside >= 0) inside else ranges.lastIndex
    joins.freezeSent = false
    player.showRange(joins.decoding)
}

/** The start of range [index] — for a freeze, the start of its hold. */
internal fun seekToStart(player: VideoPlayerState, index: Int) {
    val range = player.edit.joins.ranges[index]
    Snapshot.withMutableSnapshot { player.seekInRanges(index, if (range.isFreeze) 0L else range.fromMs) }
}

private fun SourceRange.holds(atMs: Long): Boolean = if (isFreeze) atMs == fromMs else atMs in fromMs until toMs
