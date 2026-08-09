package com.woka.core.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * What is known about one video having been watched — the resume point (§4) and the
 * grid's progress bar, from a single record.
 *
 * It replaces the old `playbackPositions: Map<String, Long>` outright rather than
 * sitting beside it. A bare position could not answer either of the two questions
 * the marker needs: how far through the file it is (no duration to divide by) and
 * whether it was ever finished — the resume logic *deletes* the record near the
 * end, so "watched" was precisely the state that could not be stored.
 *
 * [durationMs] is written by the player, which already knows it. Nothing probes a
 * file to draw a card.
 *
 * [finished] is reached two ways — playing to the end, or being marked by hand —
 * and deliberately records neither. One state, two doors; anything else would need
 * the card to explain a difference that means nothing to the person looking at it.
 */
@Immutable
@Serializable
data class WatchRecord(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val finished: Boolean = false
) {
    /** True once anything at all is known — what the "dim it" setting acts on. */
    val isStarted: Boolean get() = finished || positionMs > 0L

    /**
     * How much of the bar to fill, or null when there is nothing honest to draw.
     * A hand-marked record has no duration and still fills it: finished is finished.
     */
    val progress: Float?
        get() = when {
            finished -> 1f
            positionMs > 0L && durationMs > 0L -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            else -> null
        }
}
