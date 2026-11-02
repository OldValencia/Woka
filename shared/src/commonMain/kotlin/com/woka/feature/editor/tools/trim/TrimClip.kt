package com.woka.feature.editor.tools.trim

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * One clip of the timeline: a range of the **one** source file, under a stable id
 * (ME-210, `timeline.md` §3.1).
 *
 * **The id is minted once and never reused**, exactly as `MarkupShape.id` is: the strip's
 * selection is keyed on it and must survive a move, which replaces the instance — and a
 * per-clip edit (ME-215) will be keyed on it too, where a *position* would hand every clip
 * its neighbour's edit the moment the row was re-ordered.
 *
 * **Only what the strip's geometry needs** (§3.2). Speed and a freeze's hold will join as
 * fields with defaults that reproduce today's clip (ME-216, ME-217); volume and the picture
 * tools never will, because they change how a clip looks rather than how long it lasts.
 */
@Immutable
@Serializable
data class TrimClip(
    val id: String,
    /** Where the clip starts, in source milliseconds. */
    val fromMs: Long,
    /** Where it ends, exclusive, in source milliseconds. */
    val toMs: Long
) {
    val lengthMs: Long get() = (toMs - fromMs).coerceAtLeast(0L)
}

/**
 * Where a timeline position lands: which clip, and which source millisecond inside it.
 *
 * The index rather than the id, because the same source range may appear twice after a
 * duplicate — the source millisecond alone cannot say which copy is playing.
 */
@Immutable
data class ClipPosition(val index: Int, val sourceMs: Long)
