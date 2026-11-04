package com.woka.feature.viewer.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.woka.core.ui.image.AppliedVideoEdits
import com.woka.core.ui.image.EditedClock
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.seek

/**
 * The slider's clock for a film `Done` cut (ME-285, reported 2026-11-02: a film trimmed
 * from 26 s to 10 s still showed 0:26), or null for one it did not.
 *
 * **The same instance the walk moves** — it is cached with the folded edit — so the play
 * head is placed by the range the walk says it is in. Re-read when a save moves
 * `AppliedVideoEdits.generation`. Chapters are source time; the caller lays them on the
 * result (`chapterSegmentsOnResult`).
 */
@Composable
internal fun rememberResultClock(player: VideoPlayerState?): EditedClock? {
    val file = player?.file ?: return null
    return remember(file, AppliedVideoEdits.generation) {
        AppliedVideoEdits.editOf(file)?.clock?.takeIf { it.ranges.isNotEmpty() }
    }
}

/** A seek on the result's slider: straight through for an uncut film, mapped for a cut one. */
internal fun seekOnResult(player: VideoPlayerState?, clock: EditedClock?, ms: Long) {
    if (clock == null) {
        player?.seek(ms)
        return
    }
    val (index, sourceMs) = clock.toSource(ms)
    clock.index = index
    // Into a freeze: its frame, and how far into its hold (ME-217).
    clock.heldMs = if (clock.ranges[index].isFreeze) (ms - clock.startOf(index)).coerceAtLeast(0L) else 0L
    player?.seek(sourceMs)
}

/** The source millisecond under result position [ms]; [ms] itself for an uncut film. */
internal fun EditedClock?.sourceOf(ms: Long): Long = this?.toSource(ms)?.second ?: ms
