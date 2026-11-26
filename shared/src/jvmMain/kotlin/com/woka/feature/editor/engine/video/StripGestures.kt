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
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.move
import com.woka.feature.viewer.video.engine.pauseForSeek
import com.woka.feature.viewer.video.engine.resumeAfterSeek
import kotlin.math.abs

/**
 * The clip lane's pointer (`video-editor.md` §4.2, §4.3a; RD-301): *the body moves and the edge
 * trims*, **whichever tab is open** — the Clip tab no longer gates the handles (the author,
 * 2026-11-26).
 *
 * - A press on a handle — the film's two outer ends, both edges of the selected clip — trims.
 * - A click on a body **selects it and does not seek** ([onPicked]); the ruler moves the play
 *   head. The clip tabs (2026-11-04) existed only because a click on the body did both; with
 *   the body selecting alone they went.
 * - A drag past the touch slop carries the clip to a new slot.
 *
 * A right press never reaches this — `awaitFirstDown` waits for the primary button (measured
 * 2026-11-02) — and opens the menu instead (`TimelineMenu`).
 *
 * Every write goes through `StripState`, which is the session's preview and commit — so a drag
 * is one history entry however many slots it crossed, and none if it came back.
 */
internal fun Modifier.stripGestures(
    state: StripState,
    geometry: () -> StripGeometry,
    handlePx: Float,
    /** Where the clip's wave strip starts, from the lane's top: a click below it is on the sound. */
    waveTopPx: Float,
    onPicked: (clip: TrimClip, onWave: Boolean) -> Unit
) = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown()
        if (state.clips.isEmpty()) return@awaitEachGesture
        val layout = geometry()
        down.consume()
        val handle = handlesOf(state).minByOrNull { abs(down.position.x - it.x(layout)) }
        val toHandle = handle?.let { abs(down.position.x - it.x(layout)) } ?: Float.MAX_VALUE
        if (handle != null && toHandle <= handlePx) dragEdge(state, layout, down, handle)
        else pickOrCarry(state, layout, down) { clip -> onPicked(clip, down.position.y >= waveTopPx) }
    }
}

/** Press and drag seeks, through the `pauseForSeek` → seek → `resumeAfterSeek` sequence the viewer's slider uses. */
internal suspend fun AwaitPointerEventScope.scrub(state: StripState, geometry: StripGeometry, down: PointerInputChange) {
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
 * Selects on a click; carries on a drag. The slot is read off the layout the drag began on
 * (`StripGeometry.dropIndex`), and the live list shows the clip already in it — the mockup's
 * "the others have already slid".
 */
private suspend fun AwaitPointerEventScope.pickOrCarry(
    state: StripState,
    geometry: StripGeometry,
    down: PointerInputChange,
    onClick: (TrimClip) -> Unit
) {
    val from = state.clips
    val index = geometry.indexAt(down.position.x) ?: return
    val id = from[index].id
    val moved = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
    if (moved == null || from.size < 2) return onClick(from[index])
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
