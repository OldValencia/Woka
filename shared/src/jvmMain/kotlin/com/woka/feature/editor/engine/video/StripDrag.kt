package com.woka.feature.editor.engine.video

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.woka.feature.editor.core.EditorTimelineScope
import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.TrimTool
import com.woka.feature.viewer.video.engine.VideoPlayerState

/**
 * A drag in flight on the strip — what the drawing has to know that the document does
 * not (ME-210, ME-214).
 *
 * Both cases carry the list **as it was when the drag began**: a reorder's slots are read
 * off it rather than off the live list, so they do not shift under the pointer while it
 * decides, and the finish compares against it to know whether anything changed.
 */
internal sealed interface StripDrag {

    val from: List<TrimClip>

    /** Clip [id] is being carried; the live list already shows it in its new slot. */
    data class Reorder(val id: String, override val from: List<TrimClip>) : StripDrag

    /**
     * A clip's edge, moved [deltaMs] in source time so far — negative to the left. The
     * layout stays [from]'s until release, and the moved stretch is shaded on it.
     */
    data class Edge(
        val handle: StripHandle,
        override val from: List<TrimClip>,
        val deltaMs: Long = 0L
    ) : StripDrag
}

/** One edge the strip offers to drag: clip [index]'s start when [leading], else its end. */
internal data class StripHandle(val index: Int, val leading: Boolean) {
    fun x(geometry: StripGeometry): Float =
        geometry.spans[index].let { if (leading) it.left else it.right }
}

/**
 * The edges that are draggable now: the timeline's two outer ends always, and both edges
 * of the selected clip. Not every clip's — two handles 3 px apart at every join would be
 * a target nobody hits on purpose; selecting a clip is what gives it its own ends.
 */
internal fun handlesOf(state: StripState): List<StripHandle> = handlesOf(state.clips, state.edits.selectedId)

internal fun handlesOf(clips: List<TrimClip>, selectedId: String?): List<StripHandle> {
    if (clips.isEmpty()) return emptyList()
    val selected = clips.indexOfFirst { it.id == selectedId }
    val own = if (selected >= 0) listOf(StripHandle(selected, true), StripHandle(selected, false)) else emptyList()
    return (listOf(StripHandle(0, true), StripHandle(clips.lastIndex, false)) + own).distinct()
}

/**
 * Everything a strip gesture needs, gathered so the pointer handler can be keyed on one
 * object and read the **current** values at event time.
 *
 * The handler is keyed on this object and on whether Trim is open. Keyed on the clip
 * list it would restart mid-drag — a reorder previews a new list on every slot it crosses — and
 * the drag would die on the first one. The clip list is therefore read through [clips],
 * which the strip refreshes every composition.
 */
@Stable
internal class StripState(val player: VideoPlayerState, val walk: ClipWalk) {

    /**
     * Plain fields, written from a `SideEffect` by [refresh]: copies of what the current
     * composition was given, read by the handler at event time. The shell builds a new
     * scope object every composition, so holding the first one would write through a
     * stale `editing`; and a snapshot state written during the composition that reads it
     * would ask for that composition again.
     */
    var clips: List<TrimClip> = emptyList()
        private set
    lateinit var edits: EditorTimelineScope
        private set
    var durationMs: Long = 0L
        private set

    fun refresh(clips: List<TrimClip>, edits: EditorTimelineScope, durationMs: Long) {
        this.clips = clips
        this.edits = edits
        this.durationMs = durationMs
    }

    var drag: StripDrag? by mutableStateOf(null)

    fun preview(next: List<TrimClip>) = edits.preview(TrimTool.opFor(next))

    /**
     * Ends a drag on [last], the list the gesture previewed most recently: one history
     * entry if it differs from where the drag began, none at all if it does not — a drag
     * that ends where it started pushes nothing (`timeline.md` §5). Compared against the
     * gesture's own last list rather than [clips], which may still be a frame behind.
     */
    fun finish(last: List<TrimClip>) {
        val started = drag?.from
        drag = null
        if (started != null && last != started) edits.commit() else edits.cancel()
    }

    /** A one-shot edit from a button: previewed and committed as one step. */
    fun apply(next: List<TrimClip>) {
        if (next == clips) return
        edits.preview(TrimTool.opFor(next))
        edits.commit()
    }
}
