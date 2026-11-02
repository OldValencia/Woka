package com.woka.core.ui.image

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * A cut film's two clocks — the source the player speaks and the result the user sees —
 * and which kept range is playing now (ME-284, ME-285).
 *
 * **One per edit, shared by everything that reads it.** The walk that skips the cut-away
 * stretches moves [index]; the slider and the time labels read it to place the play head.
 * The source position alone cannot say where on the result it is, because a duplicated
 * range puts the same source on the result twice — the editor's `ClipWalk` reasons the
 * same way, over its own clip list.
 *
 * Built once per folded edit and cached with it, which is how the viewer's frame and its
 * controls — two composables that never see each other — hold the same instance.
 */
@Stable
class EditedClock(val ranges: List<SourceRange>) {

    /** Which of [ranges] the player is in. Written by the walk, read by the slider. */
    var index by mutableIntStateOf(0)

    /** How long the result is. */
    val totalMs: Long = ranges.sumOf { it.toMs - it.fromMs }

    /** Where source [sourceMs] sits on the result, inside the range the walk is in. */
    fun toTimeline(sourceMs: Long): Long {
        if (ranges.isEmpty()) return sourceMs
        val current = index.coerceIn(0, ranges.lastIndex)
        val start = ranges.take(current).sumOf { it.toMs - it.fromMs }
        val range = ranges[current]
        return start + (sourceMs - range.fromMs).coerceIn(0L, range.toMs - range.fromMs)
    }

    /**
     * The source millisecond under result position [timelineMs], and the range it is in —
     * what a seek on the result's slider must ask the player for.
     */
    fun toSource(timelineMs: Long): Pair<Int, Long> {
        if (ranges.isEmpty()) return 0 to timelineMs
        var start = 0L
        ranges.forEachIndexed { i, range ->
            val length = range.toMs - range.fromMs
            if (timelineMs < start + length) return i to range.fromMs + (timelineMs - start).coerceAtLeast(0L)
            start += length
        }
        return ranges.lastIndex to ranges.last().toMs
    }
}
