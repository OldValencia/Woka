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
    val sort: SortType = SortType.NameAsc
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
        val foldersFirst = sortedItems.sortedBy { if (it is MediaItem.Folder) 0 else 1 }
        
        FolderState(
            items = foldersFirst,
            layout = layout,
            sort = sort
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
}
