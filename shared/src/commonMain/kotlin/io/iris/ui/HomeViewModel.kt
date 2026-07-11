package io.iris.ui

import io.iris.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import io.iris.data.ViewLayout
import io.iris.data.SortType
import java.io.File

data class HomeState(
    val linkedFolders: List<String> = emptyList(),
    val layout: ViewLayout = ViewLayout.Grid,
    val sort: SortType = SortType.NameAsc,
    val showTitles: Boolean = true,
    val isSelectMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet()
)

class HomeViewModel(
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isSelectMode = MutableStateFlow(false)
    private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())
    private var lastSelectedPath: String? = null

    val state: StateFlow<HomeState> = combine(
        settingsRepository.settings,
        _isSelectMode,
        _selectedPaths
    ) { settings, isSelectMode, selectedPaths ->
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
                sort = sort,
                showTitles = settings.folderShowTitles["HOME"] ?: settings.showTitles,
                isSelectMode = isSelectMode,
                selectedPaths = selectedPaths
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

    fun setShowTitles(show: Boolean) {
        settingsRepository.setFolderShowTitles("HOME", show)
    }

    fun toggleSelectMode() {
        _isSelectMode.value = !_isSelectMode.value
        if (!_isSelectMode.value) {
            _selectedPaths.value = emptySet()
            lastSelectedPath = null
        }
    }

    fun toggleSelection(path: String) {
        if (!_isSelectMode.value) {
            _isSelectMode.value = true
        }
        val current = _selectedPaths.value.toMutableSet()
        if (current.contains(path)) current.remove(path) else current.add(path)
        _selectedPaths.value = current
        lastSelectedPath = path
    }

    fun selectRange(toPath: String) {
        if (!_isSelectMode.value) {
            _isSelectMode.value = true
        }
        val fromPath = lastSelectedPath ?: return toggleSelection(toPath)
        val currentItems = state.value.linkedFolders
        val fromIndex = currentItems.indexOf(fromPath)
        val toIndex = currentItems.indexOf(toPath)
        if (fromIndex != -1 && toIndex != -1) {
            val start = minOf(fromIndex, toIndex)
            val end = maxOf(fromIndex, toIndex)
            val rangePaths = currentItems.subList(start, end + 1)
            val current = _selectedPaths.value.toMutableSet()
            current.addAll(rangePaths)
            _selectedPaths.value = current
            lastSelectedPath = toPath
        }
    }

    fun clearSelection() {
        _selectedPaths.value = emptySet()
    }

    fun selectAll() {
        val currentItems = state.value.linkedFolders
        _selectedPaths.value = currentItems.toSet()
    }
}
