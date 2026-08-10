package com.woka.core.data

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.StateFlow

@Stable
interface SettingsRepository {
    val settings: StateFlow<WokaSettings>
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
    fun setRememberSizeState(remember: Boolean)
    fun setDefaultUseZoom(useZoom: Boolean)
    fun setDefaultZoomPercent(percent: Int)
    fun setSubtitleSize(size: SubtitleSize)
    fun setSubtitleFont(font: SubtitleFont)
    fun setSubtitleColor(argb: Long)
    fun setSubtitleBoxOpacity(opacity: Float)
    fun setSubtitleOutline(outline: Boolean)
    fun resetSubtitleAppearance()
    fun setMediaVolume(volume: Float)
    fun addPinnedSession(session: PinnedSession)
    fun removePinnedSession(path: String)
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
