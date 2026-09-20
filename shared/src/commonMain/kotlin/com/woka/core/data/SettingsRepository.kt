package com.woka.core.data

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.StateFlow

/**
 * Folders, listing and how things look.
 *
 * Split from [PlaybackSettings] when the implementation hit the file-size mandate
 * for the second time (ME-006). The first split moved the setter *bodies* to
 * `SettingsMutations`; there was nothing left to move the second time, so the
 * seam had to be the contract itself. Callers are unaffected — [SettingsRepository]
 * still extends both, so nothing that holds one has to learn which half it wants.
 */
@Stable
interface LibrarySettings {
    fun addLinkedFolder(path: String)
    fun removeLinkedFolder(path: String)
    fun setFolderLayout(path: String, layout: ViewLayout)
    fun setFolderSort(path: String, sort: SortType)
    fun setFolderShowTitles(path: String, show: Boolean)
    fun setShowTitles(show: Boolean)
    fun setShowHiddenFiles(show: Boolean)
    fun setTheme(theme: String)
    fun setCarouselMode(mode: CarouselVisibility)
    fun setAmbientFill(enabled: Boolean)
    fun setScalePreset(preset: ScalePreset)
    fun setHdrViewMode(mode: HdrViewMode)
    fun setRememberSizeState(remember: Boolean)
    fun setDefaultUseZoom(useZoom: Boolean)
    fun setDefaultZoomPercent(percent: Int)
    fun setSubtitleSize(size: SubtitleSize)
    fun setSubtitleFont(font: SubtitleFont)
    fun setSubtitleColor(argb: Long)
    fun setSubtitleBoxOpacity(opacity: Float)
    fun setSubtitleOutline(outline: Boolean)
    fun resetSubtitleAppearance()
    fun addPinnedSession(session: PinnedSession)
    fun removePinnedSession(path: String)
}

/** How media plays, which tracks it picks, and what has been watched. */
@Stable
interface PlaybackSettings {
    fun setMediaVolume(volume: Float)

    /**
     * Sound boost on or off. Turning it **off also pulls the level back to 100 %**, in
     * the same write: a stored 240 % with the switch off is a level no control could
     * then reach or lower.
     */
    fun setSoundBoost(enabled: Boolean)

    /**
     * Both layers are written on every manual switch, but each gets its own record:
     * the file's is a merge of what was ever chosen there, the folder's carries only
     * what this action chose (§2.6 / Step 15).
     */
    fun setTrackPrefs(
        filePath: String,
        folderPath: String,
        filePrefs: TrackPrefs,
        folderPrefs: TrackPrefs
    )
    fun clearTrackPrefsForFile(filePath: String)
    fun clearAllTrackPrefs()
    fun setRepeatMode(mode: RepeatMode)
    fun setShuffle(shuffle: Boolean)
    fun setPlaybackSpeed(speed: Float)
    fun setResumeMode(mode: ResumeMode)
    fun setResumeMinDuration(minutes: Int)

    /** How much of a file has to play before it is marked watched (§4). */
    fun setWatchedWhen(mode: WatchedWhen)

    /** Total or remaining in the seek bar's right-hand label. Written by the label itself. */
    fun setShowRemainingTime(enabled: Boolean)

    /** The play head; leaves [WatchRecord.finished] alone — see `withWatchPosition`. */
    fun setWatchPosition(filePath: String, positionMs: Long, durationMs: Long)

    /** Played to the end, or marked by hand from the grid. */
    fun setWatchFinished(filePath: String, durationMs: Long)

    /** Forgets a file entirely: no resume point and no mark. */
    fun clearWatchRecord(filePath: String)
    fun clearAllWatchRecords()
    fun setDimWatchedVideos(enabled: Boolean)
    fun setCarryTracksAcrossFolder(enabled: Boolean)
    fun setSystemMediaControls(enabled: Boolean)
    fun setPipAlwaysOnTop(enabled: Boolean)
}

@Stable
interface SettingsRepository : LibrarySettings, PlaybackSettings {
    val settings: StateFlow<WokaSettings>

    /** Editor tool enablement (§3.4). Writes to `editorDisabledTools`. */
    fun setEditorToolEnabled(id: String, enabled: Boolean)
}
