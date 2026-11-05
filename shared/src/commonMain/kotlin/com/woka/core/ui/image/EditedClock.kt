package com.woka.core.ui.image

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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

    /**
     * How far into a freeze the result is, and whether its hold runs (ME-217) — the walk's
     * own time, since the player stands on one frame and its position cannot say.
     */
    var heldMs by mutableLongStateOf(0L)
    var holding by mutableStateOf(false)

    /** Where range [i] starts on the result. */
    fun startOf(i: Int): Long = ranges.take(i).sumOf { it.lengthMs }

    /** How long the result is. */
    val totalMs: Long = ranges.sumOf { it.lengthMs }

    /** Where source [sourceMs] sits on the result, inside the range the walk is in. */
    fun toTimeline(sourceMs: Long): Long {
        if (ranges.isEmpty()) return sourceMs
        val current = index.coerceIn(0, ranges.lastIndex)
        val start = startOf(current)
        val range = ranges[current]
        if (range.isFreeze) return start + heldMs
        return start + ((sourceMs - range.fromMs) / range.speed.toDouble()).toLong().coerceIn(0L, range.lengthMs)
    }

    /**
     * The source millisecond under result position [timelineMs], and the range it is in —
     * what a seek on the result's slider must ask the player for.
     */
    fun toSource(timelineMs: Long): Pair<Int, Long> {
        if (ranges.isEmpty()) return 0 to timelineMs
        var start = 0L
        ranges.forEachIndexed { i, range ->
            val length = range.lengthMs
            if (timelineMs < start + length) {
                val into = if (range.isFreeze) 0L else ((timelineMs - start).coerceAtLeast(0L) * range.speed.toDouble()).toLong()
                return i to range.fromMs + into
            }
            start += length
        }
        return ranges.lastIndex to ranges.last().toMs
    }
}
