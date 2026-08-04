package com.woka.ui.viewer.player

import com.woka.AppDependencies
import com.woka.data.resumeAppliesTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** Below this there is nothing worth returning to. */
private const val MIN_RESUME_MS = 10_000L

/** Within this of the end the file counts as watched: the record is dropped. */
private const val FINISHED_TAIL_MS = 30_000L

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
    val saved = settings.playbackPositions[file.absolutePath] ?: return null
    return saved.takeIf { it in MIN_RESUME_MS until finishedFromMs() }
}

/**
 * Writes the play head, or clears the record when there is nothing to return to —
 * the file was watched to the end, or rewound to the start. Clearing is guarded on
 * a record actually existing: every write schedules a `settings.json` save, and
 * this runs every few seconds.
 */
internal fun VideoPlayerState.saveResumePosition() {
    val repository = AppDependencies.settingsRepository
    val settings = repository.settings.value
    if (!settings.resumeAppliesTo(durationMs)) return
    val path = file.absolutePath
    val existing = settings.playbackPositions[path]
    val position = currentPositionMs
    if (position < MIN_RESUME_MS || position >= finishedFromMs()) {
        if (existing != null) repository.clearPlaybackPosition(path)
        return
    }
    if (existing != position) repository.setPlaybackPosition(path, position)
}

private fun VideoPlayerState.finishedFromMs(): Long =
    (durationMs - FINISHED_TAIL_MS).coerceAtLeast(MIN_RESUME_MS + 1)
