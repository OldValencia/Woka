package io.iris.ui

import io.iris.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import io.iris.data.ViewLayout
import io.iris.data.SortType
import java.io.File

data class HomeState(
    val linkedFolders: List<String> = emptyList(),
    val layout: ViewLayout = ViewLayout.Grid,
    val sort: SortType = SortType.NameAsc
)

class HomeViewModel(
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    val state: StateFlow<HomeState> = settingsRepository.settings
        .map { settings -> 
            val rawFolders = settings.linkedFolders
            val sort = settings.folderSorts["HOME"] ?: SortType.NameAsc
            
            val sortedFolders = when (sort) {
                SortType.Custom -> rawFolders
                SortType.NameAsc -> rawFolders.sortedBy { File(it).name.lowercase() }
                SortType.NameDesc -> rawFolders.sortedByDescending { File(it).name.lowercase() }
                SortType.DateAsc -> rawFolders.sortedBy { File(it).lastModified() }
                SortType.DateDesc -> rawFolders.sortedByDescending { File(it).lastModified() }
            }
            
            HomeState(
                linkedFolders = sortedFolders,
                layout = settings.folderLayouts["HOME"] ?: ViewLayout.Grid,
                sort = sort
            )
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeState()
        )

    fun onFolderPicked(path: String) {
        settingsRepository.addLinkedFolder(path)
    }

    fun removeLinkedFolder(path: String) {
        settingsRepository.removeLinkedFolder(path)
    }

    fun reorderLinkedFolders(from: Int, to: Int) {
        settingsRepository.setFolderSort("HOME", SortType.Custom)
        settingsRepository.reorderLinkedFolders(from, to)
    }

    fun setLayout(layout: ViewLayout) {
        settingsRepository.setFolderLayout("HOME", layout)
    }

    fun setSort(sort: SortType) {
        settingsRepository.setFolderSort("HOME", sort)
    }
}
