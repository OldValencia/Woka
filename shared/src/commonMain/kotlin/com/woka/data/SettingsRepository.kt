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
    fun addPinnedSession(session: PinnedSession)
    fun removePinnedSession(path: String)
}
