package io.iris.ui

import io.iris.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FolderState(
    val items: List<MediaItem> = emptyList(),
    val layout: ViewLayout = ViewLayout.Grid,
    val sort: SortType = SortType.NameAsc,
    val showTitles: Boolean = true,
    val showFolders: Boolean = true,
    val mediaFilter: String = "All",
    val isSelectMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet()
)

class FolderViewModel(
    val path: String,
    private val repository: LocalMediaRepository,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    
    private val _rawItems = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _isSelectMode = MutableStateFlow(false)
    private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())
    private var lastSelectedPath: String? = null

    val state: StateFlow<FolderState> = combine(
        _rawItems,
        settingsRepository.settings,
        _isSelectMode,
        _selectedPaths
    ) { rawItems, settings, isSelectMode, selectedPaths ->
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
            showTitles = settings.folderShowTitles[path] ?: settings.showTitles,
            showFolders = settings.showFolders,
            mediaFilter = settings.mediaFilter,
            isSelectMode = isSelectMode,
            selectedPaths = selectedPaths
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

    fun deleteFiles(paths: List<String>) {
        scope.launch(Dispatchers.IO) {
            val desktop = java.awt.Desktop.getDesktop()
            paths.forEach { path ->
                val file = java.io.File(path)
                if (file.exists()) {
                    if (desktop.isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH)) {
                        desktop.moveToTrash(file)
                    } else {
                        if (file.isDirectory) file.deleteRecursively() else file.delete()
                    }
                }
            }
            refresh()
            clearSelection()
        }
    }

    fun duplicateFiles(paths: List<String>) {
        scope.launch(Dispatchers.IO) {
            paths.forEach { path ->
                val file = java.io.File(path)
                if (file.exists()) {
                    val nameWithoutExt = file.nameWithoutExtension
                    val ext = file.extension
                    val parent = file.parentFile
                    var newName = if (ext.isNotEmpty()) "$nameWithoutExt copy.$ext" else "$nameWithoutExt copy"
                    var newFile = java.io.File(parent, newName)
                    var counter = 1
                    while (newFile.exists()) {
                        newName = if (ext.isNotEmpty()) "$nameWithoutExt copy $counter.$ext" else "$nameWithoutExt copy $counter"
                        newFile = java.io.File(parent, newName)
                        counter++
                    }
                    if (file.isDirectory) {
                        file.copyRecursively(newFile)
                    } else {
                        file.copyTo(newFile)
                    }
                }
            }
            refresh()
        }
    }

    fun createFolder(name: String) {
        scope.launch(Dispatchers.IO) {
            var folderName = name
            var newFile = java.io.File(path, folderName)
            var counter = 1
            while (newFile.exists()) {
                folderName = "$name $counter"
                newFile = java.io.File(path, folderName)
                counter++
            }
            newFile.mkdirs()
            refresh()
        }
    }

    fun moveFiles(paths: List<String>, destinationPath: String) {
        scope.launch(Dispatchers.IO) {
            val destDir = java.io.File(destinationPath)
            if (destDir.exists() && destDir.isDirectory) {
                paths.forEach { path ->
                    val file = java.io.File(path)
                    if (file.exists()) {
                        val newFile = java.io.File(destDir, file.name)
                        if (!file.renameTo(newFile)) {
                            if (file.isDirectory) {
                                file.copyRecursively(newFile)
                                file.deleteRecursively()
                            } else {
                                file.copyTo(newFile)
                                file.delete()
                            }
                        }
                    }
                }
                refresh()
                clearSelection()
            }
        }
    }

    fun renameFile(path: String, newName: String) {
        scope.launch(Dispatchers.IO) {
            val file = java.io.File(path)
            if (file.exists()) {
                val newFile = java.io.File(file.parentFile, newName)
                if (!newFile.exists()) {
                    file.renameTo(newFile)
                    refresh()
                    clearSelection()
                }
            }
        }
    }

    fun pinFolder(path: String) {
        val file = java.io.File(path)
        if (file.isDirectory) {
            settingsRepository.addPinnedSession(PinnedSession(path = path))
        }
    }


    fun copyFiles(paths: List<String>, destinationPath: String) {
        scope.launch(Dispatchers.IO) {
            val destDir = java.io.File(destinationPath)
            if (destDir.exists() && destDir.isDirectory) {
                paths.forEach { path ->
                    val file = java.io.File(path)
                    if (file.exists()) {
                        val newFile = java.io.File(destDir, file.name)
                        if (file.isDirectory) {
                            file.copyRecursively(newFile, overwrite = true)
                        } else {
                            file.copyTo(newFile, overwrite = true)
                        }
                    }
                }
                refresh()
                clearSelection()
            }
        }
    }
}
