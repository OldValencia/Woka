package com.woka.feature.editor.tools.trim

/**
 * Timeline time and source time, as pure functions over the clip list (ME-231).
 *
 * **Every off-by-one in the feature lives here**, which is why it is `commonMain`, has no
 * player in it and is unit-tested on its own (`ClipTimelineTest`). The player keeps
 * speaking source milliseconds — `currentPositionMs` is never redefined — and everything
 * the strip shows is derived from it through these.
 *
 * Clips are butt-joined (`video-editor.md` §4.3 rule 2): clip *n* starts on the timeline
 * where clip *n − 1* ended, so the timeline has no gaps and no position of its own to
 * store. A clip's speed (ME-216) is folded in here and nowhere else: the timeline runs at
 * the source's pace divided by it.
 */

/** How long the edited result is. */
fun List<TrimClip>.totalMs(): Long = sumOf { it.lengthMs }

/** Where clip [index] starts on the timeline. */
fun List<TrimClip>.startOf(index: Int): Long = take(index).sumOf { it.lengthMs }

/**
 * The clip and source millisecond under timeline position [timelineMs], clamped into the
 * timeline. Null only for an empty list.
 *
 * A position exactly on a join belongs to the clip that **starts** there — the frame the
 * play head is about to show — except at the very end, where there is no next clip and
 * the last one's final instant is the honest answer.
 */
fun List<TrimClip>.atTimeline(timelineMs: Long): ClipPosition? {
    if (isEmpty()) return null
    var start = 0L
    val clamped = timelineMs.coerceIn(0L, totalMs())
    forEachIndexed { index, clip ->
        val end = start + clip.lengthMs
        if (clamped < end) {
            // A freeze shows its one frame for the whole hold (ME-217).
            val into = if (clip.isFreeze) 0L else ((clamped - start) * clip.speed).toLong()
            return ClipPosition(index, clip.fromMs + into)
        }
        start = end
    }
    return ClipPosition(lastIndex, last().toMs)
}

/** The inverse: where source [sourceMs] inside clip [index] sits on the timeline. */
fun List<TrimClip>.timelineOf(index: Int, sourceMs: Long): Long {
    val clip = getOrNull(index) ?: return 0L
    return startOf(index) + ((sourceMs - clip.fromMs) / clip.speed).toLong().coerceIn(0L, clip.lengthMs)
}

/**
 * Which clip a free-running source position belongs to, preferring [hint] — the clip that
 * was playing — so that a duplicated range keeps playing the copy it was in.
 */
fun List<TrimClip>.clipOf(sourceMs: Long, hint: Int): Int? {
    getOrNull(hint)?.let { if (sourceMs in it.fromMs until it.toMs) return hint }
    // A freeze spans no source: the walk holding one is still in it (ME-217).
    getOrNull(hint)?.takeIf { it.isFreeze }?.let { return hint }
    return indexOfFirst { sourceMs in it.fromMs until it.toMs }.takeIf { it >= 0 }
}

/** The whole file as one clip — what an unedited video's strip shows. */
fun wholeSource(durationMs: Long): List<TrimClip> =
    listOf(TrimClip(id = WHOLE_SOURCE_ID, fromMs = 0L, toMs = durationMs))

/**
 * The id the implicit single clip carries before anything is cut.
 *
 * Fixed rather than minted so that an untouched document and one that only *looked* at
 * the strip agree, and a selection made before the first edit survives the edit that
 * makes the list explicit.
 */
const val WHOLE_SOURCE_ID = "source"
