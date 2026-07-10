package io.iris.data

import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val settings: StateFlow<IrisSettings>
    fun addLinkedFolder(path: String)
    fun removeLinkedFolder(path: String)
    fun reorderLinkedFolders(fromIndex: Int, toIndex: Int)
    fun setFolderLayout(path: String, layout: ViewLayout)
    fun setFolderSort(path: String, sort: SortType)
    fun setTheme(theme: String)
}
