package com.woka.feature.viewer.video.engine

import com.woka.app.AppDependencies
import com.woka.core.data.resumeAppliesTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** Below this there is nothing worth returning to, on a file of any real length. */
private const val MIN_RESUME_MS = 10_000L

/** Within this of the end the file counts as watched. */
private const val FINISHED_TAIL_MS = 30_000L

/**
 * …but never more of a file than this. Thirty seconds is a rounding error at the
 * end of a film and half of a one-minute clip: with a flat tail, watching 30s of a
 * 60s video marked it fully watched, which is what the grid then drew.
 */
private const val FINISHED_TAIL_RATIO = 0.08f

/**
 * …and the same shape at the other end: ten seconds is nothing in a film and half a
 * twenty-second clip. A flat floor meant a short video could not be resumed at all —
 * it was stored, drawn on the card, and then ignored on open, which reads as the
 * setting being broken rather than being cautious. `Always` has to mean always.
 */
private const val WATCH_FLOOR_RATIO = 0.02f

/** The point past which a file counts as watched. Pure, so it can be tested. */
internal fun finishedFromMs(durationMs: Long): Long {
    if (durationMs <= 0L) return Long.MAX_VALUE
    val tail = minOf(FINISHED_TAIL_MS, (durationMs * FINISHED_TAIL_RATIO).toLong())
    return durationMs - tail
}

/**
 * The point past which a position is worth keeping — and returning to. One floor
 * for both: "far enough in to be worth storing" and "far enough in to be worth
 * seeking back to" are the same judgement, and splitting them is what left a short
 * video with a progress bar it would never honour.
 */
internal fun watchFloorMs(durationMs: Long): Long =
    minOf(MIN_RESUME_MS, (durationMs * WATCH_FLOOR_RATIO).toLong())

private val SAVE_INTERVAL = 5.seconds

/**
 * Playback resume (§4). The position is written on a timer *and* when the decoder
 * tears down: `dispose()` covers closing a file, the timer covers the app being
 * killed outright, where nothing gets to run.
 *
 * Both entry points are called from the decoder's own coroutine, so the saver is
 * its child — cancelling `grabberJob` stops it, with no extra job to track.
 */
internal fun VideoPlayerState.launchResumeSaver(scope: CoroutineScope) {
    scope.launch {
        while (isActive) {
            delay(SAVE_INTERVAL)
            saveResumePosition()
        }
    }
}

/**
 * Where this file should open, or null for the beginning — because resume is off,
 * because the file is shorter than the mode asks for, or because it was watched
 * to the end.
 */
internal fun VideoPlayerState.resumePositionMs(): Long? {
    val settings = AppDependencies.settingsRepository.settings.value
    if (!settings.resumeAppliesTo(durationMs)) return null
    val saved = settings.watchRecords[file.absolutePath] ?: return null
    // A finished file opens at the beginning, exactly as before — the mark it now
    // keeps is for the grid to draw, not somewhere to resume to.
    if (saved.finished) return null
    return saved.positionMs.takeIf { it in watchFloorMs(durationMs) until finishedFromMs(durationMs) }
}

/**
 * Writes where the file was left, in the three states the grid draws from.
 *
 * Past the tail it is **marked finished and kept**, where it used to be deleted —
 * deleting is what made "watched" unstorable. Below the resume floor the position
 * is dropped but the mark is not, which is what stops re-opening a finished file
 * from erasing it (that path opens at 0 and would otherwise clear the record on the
 * first tick).
 *
 * Every branch is guarded on the value actually changing: this runs every few
 * seconds and each write schedules a `settings.json` save.
 */
internal fun VideoPlayerState.saveResumePosition() {
    val repository = AppDependencies.settingsRepository
    val settings = repository.settings.value
    if (!settings.resumeAppliesTo(durationMs)) return
    val path = file.absolutePath
    val existing = settings.watchRecords[path]
    val position = currentPositionMs
    if (position >= finishedFromMs(durationMs)) {
        if (existing?.finished != true) repository.setWatchFinished(path, durationMs)
        return
    }
    if (position < watchFloorMs(durationMs)) {
        if (existing != null && existing.positionMs != 0L) {
            repository.setWatchPosition(path, 0L, durationMs)
        }
        return
    }
    if (existing?.positionMs != position) repository.setWatchPosition(path, position, durationMs)
}

