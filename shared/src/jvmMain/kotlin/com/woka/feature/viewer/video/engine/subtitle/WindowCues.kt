package com.woka.feature.viewer.video.engine.subtitle

import com.woka.feature.viewer.video.model.Cue
import java.util.TreeMap

/** How many cues before the play head are checked for one still showing — overlaps are rare. */
private const val LOOK_BACK = 8

/**
 * What a [SubtitleWindow] has read of its track, keyed by start on the player's clock. Kept
 * for the session, so going back into a stretch already read costs no read; small, since it
 * is text, or the compressed payloads a bitmap index keeps anyway (`BitmapCue`).
 *
 * Handed container times and moves them by [originMs] (`originUs`, VF-001), the shift the
 * whole-container pass applied before it was replaced.
 */
internal class WindowCues(private val originMs: Long) {
    private val text = TreeMap<Long, List<Cue>>()
    private val bitmap = TreeMap<Long, BitmapCue>()

    val count: Int get() = bitmap.size + text.values.sumOf { it.size }
    val retainedBytes: Int get() = bitmap.values.sumOf { it.retainedBytes }
    val textCues: List<Cue> get() = text.values.flatten()

    /** Decoded lines; one read twice — a window re-read after a seek — is kept once. */
    fun addText(found: List<Cue>) {
        found.map { it.copy(startMs = it.startMs - originMs, endMs = it.endMs - originMs) }.forEach { cue ->
            text[cue.startMs] = (text[cue.startMs].orEmpty() + cue).distinct()
        }
    }

    fun addBitmap(found: List<BitmapCue>) {
        found.map { it.shifted() }.forEach { bitmap[it.startMs] = it }
    }

    fun textAt(positionMs: Long): Cue? =
        text.headMap(positionMs, true).descendingMap().values.asSequence()
            .take(LOOK_BACK).flatten().firstOrNull { positionMs in it.startMs..it.endMs }

    /** [open] is the event still waiting for its end, in container time — shown if it covers. */
    fun bitmapAt(positionMs: Long, open: BitmapCue?): BitmapCue? =
        open?.shifted()?.takeIf { positionMs in it.startMs..it.endMs }
            ?: bitmap.headMap(positionMs, true).descendingMap().values.asSequence()
                .take(LOOK_BACK).firstOrNull { positionMs in it.startMs..it.endMs }

    private fun BitmapCue.shifted(): BitmapCue = copy(startMs = startMs - originMs, endMs = endMs - originMs)
}
