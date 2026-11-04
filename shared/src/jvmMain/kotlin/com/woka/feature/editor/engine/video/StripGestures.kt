package com.woka.feature.editor.engine.video

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import com.woka.feature.editor.tools.trim.move
import com.woka.feature.editor.tools.trim.startOf
import com.woka.feature.editor.tools.trim.trimEnd
import com.woka.feature.editor.tools.trim.trimStart
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
    editing: Boolean,
    widthPx: Float,
    gapPx: Float,
    handlePx: Float
) = pointerInput(state, editing, widthPx) {
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            if (state.clips.isEmpty()) return@awaitEachGesture
            val (lead, tail) = trimmedEnds(state.clips, state.durationMs, editing)
            val geometry = StripGeometry(state.clips, widthPx, gapPx, lead, tail)
            if (!editing) return@awaitEachGesture scrub(state, geometry, down)
            val handle = handlesOf(state).minByOrNull { abs(down.position.x - it.x(geometry)) }
            if (handle != null && abs(down.position.x - handle.x(geometry)) <= handlePx) {
                edge(state, geometry, down, handle)
            } else {
                pickOrCarry(state, geometry, down)
            }
        }
    }

private suspend fun AwaitPointerEventScope.scrub(state: StripState, geometry: StripGeometry, down: PointerInputChange) {
    // **This Clip** (ME-215): the click also picks the clip whose look is being edited.
    if (state.edits.perClip) geometry.indexAt(down.position.x)?.let { state.focus(state.clips[it]) }
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
 * Selects on a click; carries on a drag. The slot is read off the layout the drag began
 * on (`StripGeometry.dropIndex`), and the live list shows the clip already in it — the
 * mockup's "the others have already slid".
 */
private suspend fun AwaitPointerEventScope.pickOrCarry(state: StripState, geometry: StripGeometry, down: PointerInputChange) {
    val from = state.clips
    val index = geometry.indexAt(down.position.x) ?: return
    val id = from[index].id
    val moved = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
    state.edits.select(id)
    if (moved == null || from.size < 2) {
        state.walk.seekTo(state.player, from, geometry.timelineAt(down.position.x))
        return
    }
    state.drag = StripDrag.Reorder(id, from)
    var last = from
    horizontalDrag(moved.id) { change ->
        change.consume()
        val next = from.move(id, geometry.dropIndex(index, change.position.x))
        if (next != last) state.preview(next)
        last = next
    }
    state.finish(last)
}

/**
 * Drags one clip's edge. The scale is the strip's at the press — milliseconds per pixel of
 * the layout the drag began on, which stays put until release — and the picture follows
 * the edge, paused, so the frame the cut will start or end on is the one on the canvas
 * when the button is released.
 */
private suspend fun AwaitPointerEventScope.edge(
    state: StripState,
    geometry: StripGeometry,
    down: PointerInputChange,
    handle: StripHandle
) {
    val from = state.clips
    val clip = from[handle.index]
    val span = geometry.spans[handle.index]
    if (span.width <= 0f) return
    // Source milliseconds per pixel: an edge moves the clip's range, whatever its speed.
    val msPerPx = clip.sourceLengthMs / span.width
    val player = state.player
    player.pauseForSeek()
    state.drag = StripDrag.Edge(handle, from)
    var last = from
    horizontalDrag(down.id) { change ->
        change.consume()
        val delta = ((change.position.x - down.position.x) * msPerPx).toLong()
        val next = if (handle.leading) from.trimStart(clip.id, clip.fromMs + delta)
        else from.trimEnd(clip.id, clip.toMs + delta, state.durationMs)
        val moved = next[handle.index].let { if (handle.leading) it.fromMs - clip.fromMs else it.toMs - clip.toMs }
        state.drag = StripDrag.Edge(handle, from, moved)
        if (next != last) state.preview(next)
        last = next
        val start = next.startOf(handle.index)
        state.walk.seekTo(player, next, if (handle.leading) start else start + next[handle.index].lengthMs - 1L)
    }
    state.finish(last)
    player.resumeAfterSeek()
}
