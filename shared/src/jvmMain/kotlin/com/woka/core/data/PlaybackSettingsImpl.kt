package com.woka.core.data

/**
 * The [PlaybackSettings] half of the repository: how media plays, which tracks it
 * picks, and what has been watched. Shares [mutator] with [LibrarySettingsImpl],
 * so both halves write one record and one save.
 */
internal class PlaybackSettingsImpl(private val mutator: SettingsMutator) : PlaybackSettings {

    override fun setMediaVolume(volume: Float) =
        mutator.update { it.copy(mediaVolume = volume.coerceIn(0f, MAX_MEDIA_VOLUME)) }

    // Off also lowers the level: a stored 240 % with the switch off is a level the
    // slider can no longer reach, and therefore one nothing could turn down again.
    override fun setSoundBoost(enabled: Boolean) = mutator.update {
        it.copy(
            soundBoost = enabled,
            mediaVolume = if (enabled) it.mediaVolume else it.mediaVolume.coerceAtMost(1f)
        )
    }

    override fun setTrackPrefs(
        filePath: String,
        folderPath: String,
        filePrefs: TrackPrefs,
        folderPrefs: TrackPrefs
    ) = mutator.update { it.withTrackPrefs(filePath, folderPath, filePrefs, folderPrefs) }

    override fun clearTrackPrefsForFile(filePath: String) =
        mutator.update { it.copy(trackPrefsPerFile = it.trackPrefsPerFile - filePath) }

    // The escape hatch for a library-wide change of mind (Step 15): without it,
    // switching a whole season to another dub would mean visiting every file that
    // ever had an explicit choice.
    override fun clearAllTrackPrefs() =
        mutator.update { it.copy(trackPrefsPerFile = emptyMap(), trackPrefsPerFolder = emptyMap()) }

    override fun setRepeatMode(mode: RepeatMode) = mutator.update { it.copy(repeatMode = mode) }

    override fun setShuffle(shuffle: Boolean) = mutator.update { it.copy(shuffle = shuffle) }

    override fun setPlaybackSpeed(speed: Float) =
        mutator.update { it.copy(playbackSpeed = PlaybackSpeed.snap(speed)) }

    override fun setResumeMode(mode: ResumeMode) = mutator.update { it.copy(resumeMode = mode) }

    override fun setResumeMinDuration(minutes: Int) =
        mutator.update { it.copy(resumeMinDurationMinutes = minutes.coerceIn(1, 240)) }

    override fun setWatchedWhen(mode: WatchedWhen) = mutator.update { it.copy(watchedWhen = mode) }

    override fun setShowRemainingTime(enabled: Boolean) =
        mutator.update { it.copy(showRemainingTime = enabled) }

    override fun setWatchPosition(filePath: String, positionMs: Long, durationMs: Long) =
        mutator.update { it.withWatchPosition(filePath, positionMs, durationMs) }

    override fun setWatchFinished(filePath: String, durationMs: Long) =
        mutator.update { it.withWatchFinished(filePath, durationMs) }

    override fun clearWatchRecord(filePath: String) =
        mutator.update { it.copy(watchRecords = it.watchRecords - filePath) }

    override fun clearAllWatchRecords() = mutator.update { it.copy(watchRecords = emptyMap()) }

    override fun setDimWatchedVideos(enabled: Boolean) =
        mutator.update { it.copy(dimWatchedVideos = enabled) }

    override fun setCarryTracksAcrossFolder(enabled: Boolean) =
        mutator.update { it.copy(carryTracksAcrossFolder = enabled) }

    override fun setSystemMediaControls(enabled: Boolean) =
        mutator.update { it.copy(systemMediaControls = enabled) }

    override fun setPipAlwaysOnTop(enabled: Boolean) =
        mutator.update { it.copy(pipAlwaysOnTop = enabled) }
}
