package com.woka.feature.viewer.video.engine

import com.woka.app.AppDependencies
import com.woka.core.data.resumeAppliesTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

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
 * Where "watched" starts for *this* file — the setting's share, pulled earlier by
 * the container's own credits chapter when it names one (`WatchThresholds.kt`).
 */
private fun VideoPlayerState.watchedFrom(): Long = watchedFromMs(
    durationMs = durationMs,
    mode = AppDependencies.settingsRepository.settings.value.watchedWhen,
    creditsStartMs = creditsStartMs(tracks.chapters, durationMs)
)

/**
 * Where this file should open, or null for the beginning — because resume is off,
 * because the file is shorter than the mode asks for, or because it was watched.
 */
internal fun VideoPlayerState.resumePositionMs(): Long? {
    val settings = AppDependencies.settingsRepository.settings.value
    if (!settings.resumeAppliesTo(durationMs)) return null
    val saved = settings.watchRecords[file.absolutePath] ?: return null
    // A finished file opens at the beginning, exactly as before — the mark it now
    // keeps is for the grid to draw, not somewhere to resume to.
    if (saved.finished) return null
    return saved.positionMs.takeIf { it in watchFloorMs(durationMs) until watchedFrom() }
}

/**
 * Writes where the file was left, in the three states the grid draws from.
 *
 * Past the watched point it is **marked finished and kept**, where it used to be
 * deleted — deleting is what made "watched" unstorable. Below the resume floor the
 * position is dropped but the mark is not, which is what stops re-opening a
 * finished file from erasing it (that path opens at 0 and would otherwise clear
 * the record on the first tick).
 *
 * Marking is a settings write and nothing else: a file that crosses the threshold
 * mid-playback keeps playing, and the guard on `finished` means the remaining
 * ticks write nothing at all.
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
    if (position >= watchedFrom()) {
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
