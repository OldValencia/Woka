package com.woka.feature.folder

import com.woka.core.data.LocalMediaRepository
import com.woka.core.data.MediaItem
import com.woka.core.data.PinnedSession
import com.woka.core.data.SettingsRepository
import com.woka.core.data.SortType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * Folder screen ViewModel: listing, sorting and multi-selection. State derivation
 * lives in `FolderStateMapper`, filesystem operations in `FolderFileOperations`
 * (§2 file-size mandate) — both need [scope] / [settingsRepository], hence the
 * `internal` visibility.
 */
class FolderViewModel(
    val path: String,
    private val repository: LocalMediaRepository,
    internal val settingsRepository: SettingsRepository
) {
    internal val scope = CoroutineScope(Dispatchers.Main)

    private val _rawItems = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _isSelectMode = MutableStateFlow(false)
    private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())
    private var lastSelectedPath: String? = null

    /**
     * Projected down to [FolderSettings] and de-duplicated before the combine, so
     * an unrelated write (volume, resume position) no longer rebuilds the listing;
     * `flowOn` keeps the sort and filter off the UI thread (§10.2). `stateIn` still
     * shares on [scope], which is where collectors want the value.
     */
    val state: StateFlow<FolderState> = combine(
        _rawItems,
        settingsRepository.settings.map { it.folderSettings(path) }.distinctUntilChanged(),
        _isSelectMode,
        _selectedPaths
    ) { rawItems, settings, isSelectMode, selectedPaths ->
        buildFolderState(rawItems, settings, isSelectMode, selectedPaths)
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.WhileSubscribed(5000), FolderState())

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

    fun setSort(sort: SortType) {
        settingsRepository.setFolderSort(path, sort)
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
        if (current.contains(path)) {
            current.remove(path)
            if (lastSelectedPath == path) {
                lastSelectedPath = null
            }
        } else {
            current.add(path)
            lastSelectedPath = path
        }
        _selectedPaths.value = current
    }

    fun selectRange(toPath: String) {
        if (!_isSelectMode.value) {
            _isSelectMode.value = true
        }
        val fromPath = lastSelectedPath
        if (fromPath == null) {
            toggleSelection(toPath)
            return
        }
        val currentItems = state.value.items
        val fromIndex = currentItems.indexOfFirst { it.file.absolutePath == fromPath }
        val toIndex = currentItems.indexOfFirst { it.file.absolutePath == toPath }
        if (fromIndex != -1 && toIndex != -1) {
            val start = minOf(fromIndex, toIndex)
            val end = maxOf(fromIndex, toIndex)
            val rangePaths = currentItems.subList(start, end + 1).map { it.file.absolutePath }
            val current = _selectedPaths.value.toMutableSet()
            current.addAll(rangePaths)
            _selectedPaths.value = current
        }
    }

    fun clearSelection() {
        _selectedPaths.value = emptySet()
    }

    fun selectAll() {
        val currentItems = state.value.items
        _selectedPaths.value = currentItems.map { it.file.absolutePath }.toSet()
    }

    fun refresh() {
        loadItems()
    }

    fun pinFolder(path: String) {
        val file = File(path)
        if (file.isDirectory) {
            settingsRepository.addPinnedSession(PinnedSession(path = path))
        }
    }
}
