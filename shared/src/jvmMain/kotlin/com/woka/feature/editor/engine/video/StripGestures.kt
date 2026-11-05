package com.woka.feature.editor.engine.video

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import com.woka.feature.editor.core.NO_CLIP
import com.woka.feature.editor.tools.trim.move
import com.woka.feature.viewer.video.engine.pauseForSeek
import com.woka.feature.viewer.video.engine.resumeAfterSeek
import kotlin.math.abs

/**
 * The strip's pointer, in two vocabularies (`video-editor.md` §4.2, §4.3a).
 *
 * - **At rest** it is the viewer's scrubber: press and drag seeks, through the
 *   `pauseForSeek` → seek → `resumeAfterSeek` sequence `MediaViewerTimeSlider` uses.
 * - **With Trim open**, *the body moves and the edge trims*: a click selects a clip and
 *   puts the play head under the pointer, a drag past the touch slop carries the clip to
 *   a new slot, and a press on a handle trims that edge — the timeline's two outer ends,
 *   and **both edges of the selected clip** (reported 2026-11-02: after a split neither
 *   half had an end of its own to adjust).
 * - **A right-click resets the cut**, in either state (the author, 2026-11-02) — through
 *   the app's own `resetOnRightClick` on the strip, not here: `awaitFirstDown` waits for
 *   the primary button only, measured the same day, so a right press never reaches this.
 *
 * Every write goes through `StripState`, which is the session's preview and commit — so a
 * drag is one history entry however many slots it crossed, and none if it came back.
 */
internal fun Modifier.stripGestures(
    state: StripState,
    zoom: StripZoom,
    editing: Boolean,
    widthPx: Float,
    gapPx: Float,
    handlePx: Float
) = pointerInput(state, editing, widthPx) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (state.clips.isEmpty()) return@awaitEachGesture
            val (lead, tail) = trimmedEnds(state.clips, state.durationMs, editing)
            val geometry = StripGeometry(state.clips, widthPx, gapPx, lead, tail, zoom.scale, zoom.scrollPx)
            val head = geometry.xOf(state.walk.timelineMs(state.clips, state.player.currentPositionMs))
            // ✂ and ❄ take their own clicks; the strip must not move the play head under them.
            if (editing && state.drag == null && onPlayHeadAction(down.position, head, widthPx)) return@awaitEachGesture
            down.consume()
            if (!editing) return@awaitEachGesture scrub(state, geometry, down)
            val handle = handlesOf(state).minByOrNull { abs(down.position.x - it.x(geometry)) }
            val toHandle = handle?.let { abs(down.position.x - it.x(geometry)) } ?: Float.MAX_VALUE
            // The play head is grabbed in Trim too (2026-11-04), but an edge in reach wins: a trim
            // leaves the head on its edge, and the next press there scrubbed (measured 2026-11-05).
            val toHead = abs(down.position.x - head)
            when {
                handle != null && toHandle <= handlePx -> dragEdge(state, geometry, down, handle)
                toHead <= handlePx -> scrub(state, geometry, down)
                else -> pickOrCarry(state, geometry, down)
            }
        }
    }

private suspend fun AwaitPointerEventScope.scrub(state: StripState, geometry: StripGeometry, down: PointerInputChange) {
    val player = state.player
    player.pauseForSeek()
    state.walk.seekTo(player, state.clips, geometry.timelineAt(down.position.x))
    horizontalDrag(down.id) { change ->
        change.consume()
        state.walk.seekTo(player, state.clips, geometry.timelineAt(change.position.x))
    }
    player.resumeAfterSeek()
}

/**
 * Seeks on a click; selects and carries on a drag. The slot is read off the layout the drag began
 * on (`StripGeometry.dropIndex`), and the live list shows the clip already in it — the
 * mockup's "the others have already slid".
 */
private suspend fun AwaitPointerEventScope.pickOrCarry(state: StripState, geometry: StripGeometry, down: PointerInputChange) {
    val from = state.clips
    val index = geometry.indexAt(down.position.x) ?: return
    val id = from[index].id
    val moved = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
    // A click on the body seeks and only seeks — the clip's tab selects (2026-11-04).
    if (moved == null || from.size < 2) {
        state.walk.seekTo(state.player, from, geometry.timelineAt(down.position.x))
        return
    }
    // Selected inside the reorder's own step (ME-221) — and put back if the drop makes none, or
    // the selection moved with no step to undo it by (1.0.6 review).
    val before = state.edits.selectedId
    state.edits.pick(id, from[index].key, step = false)
    state.drag = StripDrag.Reorder(id, from)
    var last = from
    // Finished whatever ends the gesture, as an edge drag is (`dragEdge`).
    try {
        horizontalDrag(moved.id) { change ->
            change.consume()
            val next = from.move(id, geometry.dropIndex(index, change.position.x))
            if (next != last) state.preview(next)
            last = next
        }
    } finally {
        if (last == from) state.edits.pick(before, from.firstOrNull { it.id == before }?.key ?: NO_CLIP, step = false)
        state.finish(last)
    }
}
