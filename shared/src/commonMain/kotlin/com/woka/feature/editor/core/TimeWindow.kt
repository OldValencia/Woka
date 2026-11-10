package com.woka.feature.editor.core

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * When something is present on a film's timeline, and how it comes and goes (ME-263): a markup
 * shape now, a sound entry on the second row later (`audio.md` §3.3a) — one group of four
 * numbers and one curve for both, so the canvas, the viewer and the render cannot disagree.
 *
 * All in **timeline** milliseconds — the edited result's clock, not the source's. [holdMs] is the
 * whole time present; [fadeInMs] and [fadeOutMs] fall inside it, never added to it.
 */
@Immutable
@Serializable
data class TimeWindow(
    val atMs: Long,
    val holdMs: Long,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
) {

    /** The first millisecond no longer present. */
    val endMs: Long get() = atMs + holdMs.coerceAtLeast(0L)

    /**
     * How present this is at [timelineMs], `0f..1f`: zero outside the window, rising through
     * the fade in, falling through the fade out.
     *
     * **The two ramps are taken as the lower of the two**, not chained: fades longer than the
     * window then meet in the middle below full, rather than one of them being cut off — and
     * the curve still starts and ends at zero.
     */
    fun envelopeAt(timelineMs: Long): Float {
        if (timelineMs < atMs || timelineMs >= endMs) return 0f
        val rising = ramp(timelineMs - atMs, fadeInMs)
        val falling = ramp(endMs - timelineMs, fadeOutMs)
        return minOf(rising, falling)
    }

    /** [elapsedMs] into a ramp [lengthMs] long; no ramp is full at once. */
    private fun ramp(elapsedMs: Long, lengthMs: Long): Float =
        if (lengthMs <= 0L) 1f else (elapsedMs.toFloat() / lengthMs).coerceIn(0f, 1f)
}
