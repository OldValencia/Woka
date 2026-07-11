package io.iris.ui

import io.iris.data.LocalMediaRepository
import io.iris.data.MediaItem
import io.iris.data.SettingsRepository
import io.iris.data.ViewLayout
import io.iris.data.SortType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class FolderState(
    val items: List<MediaItem> = emptyList(),
    val layout: ViewLayout = ViewLayout.Grid,
    val sort: SortType = SortType.NameAsc,
    val showTitles: Boolean = true,
    val showFolders: Boolean = true,
    val mediaFilter: String = "All"
)

class FolderViewModel(
    val path: String,
    private val repository: LocalMediaRepository,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    
    private val _rawItems = MutableStateFlow<List<MediaItem>>(emptyList())

    val state: StateFlow<FolderState> = combine(
        _rawItems,
        settingsRepository.settings
    ) { rawItems, settings ->
        val layout = settings.folderLayouts[path] ?: ViewLayout.Grid
        val sort = settings.folderSorts[path].let {
            if (it == SortType.Custom) SortType.NameAsc else (it ?: SortType.NameAsc)
        }
        
        val sortedItems = when (sort) {
            SortType.NameAsc, SortType.Custom -> rawItems.sortedBy { it.file.name.lowercase() }
            SortType.NameDesc -> rawItems.sortedByDescending { it.file.name.lowercase() }
            SortType.DateAsc -> rawItems.sortedBy { it.file.lastModified() }
            SortType.DateDesc -> rawItems.sortedByDescending { it.file.lastModified() }
        }
        
        // Folders first
        val filteredItems = if (settings.mediaFilter == "Photos") {
            sortedItems.filter { it is MediaItem.Folder || it.file.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic") }
        } else if (settings.mediaFilter == "Videos") {
            sortedItems.filter { it is MediaItem.Folder || it.file.extension.lowercase() in listOf("mp4", "mkv", "avi", "mov", "webm") }
        } else {
            sortedItems
        }
        val foldersFirst = filteredItems.sortedBy { if (it is MediaItem.Folder) 0 else 1 }
        
        FolderState(
            items = foldersFirst,
            layout = layout,
            sort = sort,
            showTitles = settings.showTitles,
            showFolders = settings.showFolders,
            mediaFilter = settings.mediaFilter
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), FolderState())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadItems()
    }

    private fun loadItems() {
        scope.launch {
            _isLoading.value = true
            _rawItems.value = repository.getItems(path)
            _isLoading.value = false
        }
    }

    fun setLayout(layout: ViewLayout) {
        settingsRepository.setFolderLayout(path, layout)
    }

    fun setSort(sort: SortType) {
        settingsRepository.setFolderSort(path, sort)
    }

    fun setShowTitles(show: Boolean) {
        settingsRepository.setShowTitles(show)
    }

    fun setShowFolders(show: Boolean) {
        settingsRepository.setShowFolders(show)
    }

    fun setMediaFilter(filter: String) {
        settingsRepository.setMediaFilter(filter)
    }
}
