package com.woka.feature.editor.engine.video

import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.atTimeline
import com.woka.feature.editor.tools.trim.startOf
import com.woka.feature.editor.tools.trim.totalMs

/**
 * Where every clip of the strip sits, in pixels, and the two conversions the gestures
 * need (ME-210, ME-214).
 *
 * **Linear in time, with the gaps taken out first.** Each join is a fixed [gapPx] of air
 * — the mockup's 3 px — and the rest of the width is shared out by length, so a clip's
 * width is honest about how long it lasts. A minimum drawn width would make a 40 ms clip
 * findable and the strip dishonest everywhere else (`timeline.md` §5); it is not taken.
 *
 * **With Trim open it also lays out what was trimmed off the ends** (reported by the author
 * 2026-11-02): a strip that re-flowed the kept part to full width on release put the right
 * handle back at 100 % and showed neither what was cut nor how much. The cut-off stretches
 * stay where they were, dimmed, and since kept plus trimmed is the whole source, a trim
 * moves the handle and nothing else.
 *
 * **Zoomed, it lays the film across several strip-widths and scrolls** (ME-218): every x
 * it answers is already on screen, so the gestures and the play head need not know.
 *
 * Pure, and built per layout rather than remembered: a dozen clips are a dozen additions,
 * and a stale geometry under a pointer is the bug a cache here would invite.
 */
internal class StripGeometry(
    private val clips: List<TrimClip>,
    private val widthPx: Float,
    private val gapPx: Float,
    /** Source trimmed off the first clip's start, drawn dimmed before it — [trimmedEnds]. */
    private val leadMs: Long = 0L,
    /** Source trimmed off the last clip's end, drawn dimmed after it. */
    private val tailMs: Long = 0L,
    /**
     * A fixed scale (ME-218), or null to fit the whole film in [widthPx]. Zoomed, the scale is
     * pixels per millisecond and not a multiple of the fit: a hold dragged longer would
     * otherwise rescale the whole strip under the pointer (reported 2026-11-04).
     */
    scale: Float? = null,
    /** How far the laid-out content is moved left, so every x answered is on screen. */
    private val scrollPx: Float = 0f
) {
    private val totalMs = clips.totalMs() + leadMs + tailMs

    private val fitPx = (widthPx - gapPx * (clips.size - 1)).coerceAtLeast(0f)

    /** The scale that fits the whole film — what a zoom is measured against. */
    val fitPxPerMs = if (totalMs <= 0L) 0f else fitPx / totalMs

    /** One scale for the whole strip — what makes a drag's pixels and its milliseconds agree. */
    val pxPerMs = scale ?: fitPxPerMs

    private val usablePx = totalMs * pxPerMs

    /** The strip's own width, and the whole laid-out film's, zoomed. */
    val viewPx: Float = widthPx
    val contentPx: Float = usablePx + gapPx * (clips.size - 1).coerceAtLeast(0)

    /** The dimmed stretch before the first clip; zero wide when nothing is trimmed there. */
    val lead = ClipSpan(-scrollPx, leadMs * pxPerMs)

    /** Every clip's left edge and width, in list order. */
    val spans: List<ClipSpan> = buildList {
        var x = lead.right
        clips.forEach { clip ->
            val width = clip.lengthMs * pxPerMs
            add(ClipSpan(x, width))
            x += width + gapPx
        }
    }

    /** The dimmed stretch after the last clip. */
    val tail = ClipSpan(spans.lastOrNull()?.right ?: lead.right, tailMs * pxPerMs)

    /** The x a timeline position is drawn at — the play head's place. */
    fun xOf(timelineMs: Long): Float {
        val at = clips.atTimeline(timelineMs) ?: return 0f
        val clip = clips[at.index]
        val span = spans[at.index]
        // Timeline time across the span — true at any speed, and through a freeze's hold,
        // where the source does not move at all (ME-216, ME-217).
        if (clip.lengthMs <= 0L) return span.left
        val into = (timelineMs - clips.startOf(at.index)).coerceIn(0L, clip.lengthMs)
        return span.left + span.width * into / clip.lengthMs
    }

    /**
     * The timeline position under [x]. A point in a gap belongs to the clip on its left,
     * at that clip's end, so a click between two clips lands on the join rather than
     * nowhere.
     */
    fun timelineAt(x: Float): Long {
        if (clips.isNotEmpty() && x < spans.first().left) return 0L
        val index = indexAt(x) ?: return 0L
        val span = spans[index]
        val fraction = if (span.width <= 0f) 0f else ((x - span.left) / span.width).coerceIn(0f, 1f)
        return clips.startOf(index) + (clips[index].lengthMs * fraction).toLong()
    }

    /** Which clip [x] falls on, the gap after a clip counting as that clip. */
    fun indexAt(x: Float): Int? {
        if (clips.isEmpty()) return null
        val index = spans.indexOfLast { x >= it.left }
        return index.coerceAtLeast(0)
    }

    /**
     * Where clip [index] lands if dropped with the pointer at [x]: the number of *other*
     * clips whose middle the pointer has passed. Read off the layout the drag started on,
     * so the slots do not move under the pointer while it decides.
     */
    fun dropIndex(index: Int, x: Float): Int =
        spans.withIndex().count { (i, span) -> i != index && x > span.left + span.width / 2f }
}

/** One clip's place in the strip, in pixels from the strip's left edge. */
internal data class ClipSpan(val left: Float, val width: Float) {
    val right: Float get() = left + width

    /**
     * The part of this span inside a strip [viewPx] wide, or null when none of it is (ME-218).
     * A zoomed clip can be hundreds of thousands of pixels wide — past what a layout can
     * measure — so only this part is ever composed.
     */
    fun visibleIn(viewPx: Float): ClipSpan? {
        val from = left.coerceAtLeast(0f)
        val to = right.coerceAtMost(viewPx)
        return if (to > from) ClipSpan(from, to - from) else null
    }
}

/**
 * How much source the first clip has lost off its start and the last clip off its end —
 * what the strip draws dimmed while Trim is open, and nothing at rest, where the strip shows
 * only the result.
 */
internal fun trimmedEnds(clips: List<TrimClip>, durationMs: Long, editing: Boolean): Pair<Long, Long> {
    if (!editing || clips.isEmpty()) return 0L to 0L
    return clips.first().fromMs to (durationMs - clips.last().toMs).coerceAtLeast(0L)
}
