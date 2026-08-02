package com.woka.ui

import com.woka.data.MediaItem
import com.woka.data.SortType
import com.woka.data.ViewLayout
import com.woka.data.WokaSettings

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

/**
 * Derives the folder's visible state from the raw listing plus settings, split
 * out of [FolderViewModel] per the §2 file-size mandate. Pure, so the ViewModel
 * is left holding only the flow wiring.
 */
internal fun buildFolderState(
    path: String,
    rawItems: List<MediaItem>,
    settings: WokaSettings,
    isSelectMode: Boolean,
    selectedPaths: Set<String>
): FolderState {
    val layout = settings.folderLayouts[path] ?: ViewLayout.Grid
    val sort = settings.folderSorts[path] ?: SortType.NameAsc

    val sortedItems = when (sort) {
        SortType.NameAsc, SortType.Custom -> rawItems.sortedBy { it.file.name.lowercase() }
        SortType.NameDesc -> rawItems.sortedByDescending { it.file.name.lowercase() }
        SortType.DateAsc -> rawItems.sortedBy { it.file.lastModified() }
        SortType.DateDesc -> rawItems.sortedByDescending { it.file.lastModified() }
    }

    val visibleItems = if (settings.showHiddenFiles) {
        sortedItems
    } else {
        sortedItems.filter { !it.file.isHidden }
    }

    val filteredItems = when (settings.mediaFilter) {
        "Photos" -> visibleItems.filter {
            it is MediaItem.Folder || it.file.extension.lowercase() in listOf(
                "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic"
            )
        }

        "Videos" -> visibleItems.filter {
            it is MediaItem.Folder || it.file.extension.lowercase() in listOf(
                "mp4", "mkv", "avi", "mov", "webm"
            )
        }

        else -> visibleItems
    }
    // Folders first
    val foldersFirst = filteredItems.sortedBy { if (it is MediaItem.Folder) 0 else 1 }

    return FolderState(
        items = foldersFirst,
        layout = layout,
        sort = sort,
        showTitles = settings.folderShowTitles[path] ?: settings.showTitles,
        showFolders = settings.showFolders,
        mediaFilter = settings.mediaFilter,
        isSelectMode = isSelectMode,
        selectedPaths = selectedPaths
    )
}
