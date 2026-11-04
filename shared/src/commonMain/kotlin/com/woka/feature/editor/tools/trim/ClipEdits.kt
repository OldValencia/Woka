package com.woka.feature.editor.tools.trim

/**
 * Every edit the strip makes, as a function from one clip list to the next (ME-210,
 * ME-212, ME-213, ME-214).
 *
 * **Pure, and each returns the receiver unchanged when it has nothing to do** — a split on
 * a join, a delete of the last clip, a move to where the clip already is. The strip
 * compares the result with what it had and pushes no history entry for an equal list, so
 * "nothing happened" is decided once, here, rather than by every gesture.
 */

/**
 * The shortest clip any edit may leave, 100 ms.
 *
 * Shorter than a frame at 10 fps and longer than two at 30: a split this close to a join
 * would produce a clip the strip cannot draw wider than its gap and nobody meant to make
 * (`timeline.md` §5, the one-frame clip). A freeze-frame (ME-217) will be the only clip
 * allowed below it.
 */
const val MIN_CLIP_MS = 100L

/**
 * Splits the clip under [timelineMs] in two at that millisecond, with **no keyframe
 * consulted** (`timeline.md` §3.3). The left half keeps the old id, so a selection on it
 * stays where the user was looking; the right half takes [newId] and a fresh layer key,
 * onto which the caller copies the left half's look (ME-215).
 */
fun List<TrimClip>.split(timelineMs: Long, newId: String): List<TrimClip> {
    val at = atTimeline(timelineMs) ?: return this
    val clip = this[at.index]
    if (at.sourceMs - clip.fromMs < MIN_CLIP_MS || clip.toMs - at.sourceMs < MIN_CLIP_MS) return this
    val left = clip.copy(toMs = at.sourceMs)
    val right = clip.copy(id = newId, fromMs = at.sourceMs, key = nextKey())
    return take(at.index) + left + right + drop(at.index + 1)
}

/** Removes clip [id]; the hole closes by construction. The last clip is never removed. */
fun List<TrimClip>.delete(id: String): List<TrimClip> =
    if (size <= 1) this else filterNot { it.id == id }

/** A copy of clip [id] under [newId] and a fresh layer key, immediately after the original. */
fun List<TrimClip>.duplicate(id: String, newId: String): List<TrimClip> {
    val index = indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return this
    val copy = this[index].copy(id = newId, key = nextKey())
    return take(index + 1) + copy + drop(index + 1)
}

/** Clip [id] taken out and re-inserted at [toIndex] of the list without it. */
fun List<TrimClip>.move(id: String, toIndex: Int): List<TrimClip> {
    val clip = firstOrNull { it.id == id } ?: return this
    val rest = filterNot { it.id == id }
    val target = toIndex.coerceIn(0, rest.size)
    return rest.take(target) + clip + rest.drop(target)
}

/**
 * Moves clip [id]'s start to source [fromMs], held inside the source and at least
 * [MIN_CLIP_MS] short of the clip's own end.
 */
fun List<TrimClip>.trimStart(id: String, fromMs: Long): List<TrimClip> = map { clip ->
    if (clip.id != id) clip
    else clip.copy(fromMs = fromMs.coerceIn(0L, clip.toMs - MIN_CLIP_MS))
}

/** Moves clip [id]'s end to source [toMs], held inside [durationMs] and past its start. */
fun List<TrimClip>.trimEnd(id: String, toMs: Long, durationMs: Long): List<TrimClip> = map { clip ->
    if (clip.id != id) clip
    else clip.copy(toMs = toMs.coerceIn(clip.fromMs + MIN_CLIP_MS, durationMs))
}

/** A layer key no clip holds yet (ME-215). */
fun List<TrimClip>.nextKey(): Int = (maxOfOrNull { it.key } ?: 0) + 1

/**
 * Clip [id] joined back to the neighbour that continues it in the source — the right one
 * first, then the left — undoing a split (reported 2026-11-04: nothing could). The clip
 * that comes first keeps its id and its look. Unchanged when neither neighbour continues it
 * at the same speed: one clip has one speed (ME-216).
 */
fun List<TrimClip>.join(id: String): List<TrimClip> {
    val index = indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return this
    val clip = this[index]
    val right = getOrNull(index + 1)
    if (right != null && right.fromMs == clip.toMs && right.speed == clip.speed) {
        return take(index) + clip.copy(toMs = right.toMs) + drop(index + 2)
    }
    val left = getOrNull(index - 1)
    if (left != null && left.toMs == clip.fromMs && left.speed == clip.speed) {
        return take(index - 1) + left.copy(toMs = clip.toMs) + drop(index + 1)
    }
    return this
}

/**
 * Whether this list cuts nothing — one clip from the start to [durationMs]. Written as no
 * cut at all, so a film split and joined back is not left marked as edited.
 */
fun List<TrimClip>.cutsNothing(durationMs: Long): Boolean =
    isEmpty() || (size == 1 && first().fromMs <= 0L && first().toMs >= durationMs && first().speed == 1f)

/** Clip [id] at [speed] (ME-216); unchanged when it already plays at it. */
fun List<TrimClip>.withSpeed(id: String, speed: Float): List<TrimClip> =
    if (none { it.id == id && it.speed != speed }) this
    else map { if (it.id == id) it.copy(speed = speed) else it }

