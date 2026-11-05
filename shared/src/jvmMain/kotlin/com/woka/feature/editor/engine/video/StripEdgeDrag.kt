package com.woka.feature.editor.engine.video

import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.startOf
import com.woka.feature.editor.tools.trim.trimEnd
import com.woka.feature.editor.tools.trim.trimStart
import com.woka.feature.editor.tools.trim.withHold
import com.woka.feature.viewer.video.engine.pauseForSeek
import com.woka.feature.viewer.video.engine.resumeAfterSeek

/**
 * Drags one clip's edge. The scale is the strip's at the press, and the strip keeps it until
 * release while it re-flows around the edge (`ClipStrip`) — and the picture follows
 * the edge, paused, so the frame the cut will start or end on is the one on the canvas
 * when the button is released. Split out of `StripGestures` at the size mandate.
 *
 * **The edge stays where it is drawn at the press**, and moves by what the clamped value
 * moved, not with the pointer (measured 2026-11-05): snapping it under the pointer slid the
 * whole strip a few pixels and the far end off it, and an edge past the film's end or the
 * clip's other edge must stop where the value stops.
 *
 * **It is finished whatever ends the gesture** (measured the same day): a width change
 * cancelled the drag mid-way, the cut stayed in flight, and `Done` — which writes what is
 * committed — wrote none of it.
 */
internal suspend fun AwaitPointerEventScope.dragEdge(
    state: StripState,
    geometry: StripGeometry,
    down: PointerInputChange,
    handle: StripHandle
) {
    val from = state.clips
    val clip = from[handle.index]
    val span = geometry.spans[handle.index]
    if (span.width <= 0f) return
    // Source milliseconds per pixel: an edge moves the clip's range, whatever its speed. A
    // freeze's edge moves its hold instead, in timeline milliseconds (ME-217).
    val msPerPx = (if (clip.isFreeze) clip.lengthMs else clip.sourceLengthMs) / span.width
    val player = state.player
    player.pauseForSeek()
    val edgeAt = handle.x(geometry)
    state.drag = StripDrag.Edge(handle, from, 0L, geometry.pxPerMs, edgeAt)
    var last = from
    try {
        horizontalDrag(down.id) { change ->
            change.consume()
            val delta = ((change.position.x - down.position.x) * msPerPx).toLong()
            val next = moveEdge(from, clip, handle, delta, state.durationMs)
            val moved = next[handle.index].let { if (handle.leading) it.fromMs - clip.fromMs else it.toMs - clip.toMs }
            val shown = if (clip.isFreeze) (next[handle.index].holdMs - clip.holdMs) * if (handle.leading) -1 else 1 else moved
            state.drag = StripDrag.Edge(handle, from, moved, geometry.pxPerMs, edgeAt + shown / msPerPx)
            if (next != last) state.preview(next)
            last = next
            val start = next.startOf(handle.index)
            state.walk.seekTo(player, next, if (handle.leading) start else start + next[handle.index].lengthMs - 1L)
        }
    } finally {
        state.finish(last)
        player.resumeAfterSeek()
    }
}

/** [clip]'s [handle] moved [delta] ms, held inside the film and past the clip's other edge. */
private fun moveEdge(from: List<TrimClip>, clip: TrimClip, handle: StripHandle, delta: Long, durationMs: Long): List<TrimClip> =
    when {
        clip.isFreeze -> from.withHold(clip.id, clip.holdMs + if (handle.leading) -delta else delta)
        handle.leading -> from.trimStart(clip.id, clip.fromMs + delta)
        else -> from.trimEnd(clip.id, clip.toMs + delta, durationMs)
    }
