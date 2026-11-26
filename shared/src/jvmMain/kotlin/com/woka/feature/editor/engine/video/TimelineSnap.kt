package com.woka.feature.editor.engine.video

import com.woka.feature.editor.tools.trim.TrimClip
import com.woka.feature.editor.tools.trim.startOf
import com.woka.feature.editor.tools.trim.totalMs
import kotlin.math.abs

/**
 * Snapping for what is dragged along the timeline (RD-301, the prototype's `snapTime`; the
 * first piece of ME-220): a Markup bar's start, end or whole body lands on the film's start,
 * the play head, a join between clips or the film's end when it comes within reach — 8 dp,
 * turned into milliseconds at the lanes' scale. Alt held, nothing snaps.
 *
 * Pure, so the rule is asserted without a window (`TimelineSnapTest`).
 */
internal fun snapPoints(clips: List<TrimClip>, playHeadMs: Long): List<Long> = buildList {
    add(0L)
    add(playHeadMs)
    for (index in 1 until clips.size) add(clips.startOf(index))
    add(clips.totalMs())
}

/** [valueMs] on the nearest of [points] no further than [reachMs], or null when none is that near. */
internal fun snapTo(valueMs: Long, points: List<Long>, reachMs: Long): Long? =
    points.filter { abs(it - valueMs) <= reachMs }.minByOrNull { abs(it - valueMs) }

/**
 * A window moved whole: its start snaps first, else its end — the prototype's order — and the
 * point it snapped to, for the guide. Null [snapped] when neither came near.
 */
internal data class Snapped(val atMs: Long, val snapped: Long?)

internal fun snapBody(atMs: Long, holdMs: Long, points: List<Long>, reachMs: Long): Snapped {
    snapTo(atMs, points, reachMs)?.let { return Snapped(it, it) }
    snapTo(atMs + holdMs, points, reachMs)?.let { return Snapped(it - holdMs, it) }
    return Snapped(atMs, null)
}
