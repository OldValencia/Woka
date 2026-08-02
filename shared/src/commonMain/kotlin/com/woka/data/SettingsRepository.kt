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
    fun setZoomPopoverView(view: ZoomPopoverView)
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
}
