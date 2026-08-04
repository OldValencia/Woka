package com.woka.data

import kotlinx.coroutines.flow.StateFlow

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
    fun setPlaybackPosition(filePath: String, positionMs: Long)
    fun clearPlaybackPosition(filePath: String)
    fun setCarryTracksAcrossFolder(enabled: Boolean)
    fun setSystemMediaControls(enabled: Boolean)
}
