package com.woka.feature.folder

import com.woka.core.data.MediaItem
import com.woka.core.data.SortType
import com.woka.core.data.ViewLayout
import com.woka.core.data.WokaSettings

data class FolderState(
    val items: List<MediaItem> = emptyList(),
    val layout: ViewLayout = ViewLayout.Grid,
    val sort: SortType = SortType.NameAsc,
    val showTitles: Boolean = true,
    val showFolders: Boolean = true,
    val mediaFilter: String = "All",
    val isSelectMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    /**
     * Whether the media draws as the photo mosaic (SIDE-004). Decided on the folder
     * **before** the Photos/Videos filter: filtering a photo folder down to its two
     * clips used to flip it to cards, the layout changing under a control that only
     * meant to narrow what is shown (2026-10-17).
     */
    val isMosaic: Boolean = false
)

/**
 * The six settings values this folder actually depends on. The mapper used to take
 * the whole [WokaSettings], so it re-ran on every write to any setting — including
 * the volume slider and the 5-second resume position (§10.2). Projecting first and
 * comparing on this type instead means those writes stop reaching the folder.
 */
internal data class FolderSettings(
    val layout: ViewLayout,
    val sort: SortType,
    val showTitles: Boolean,
    val showFolders: Boolean,
    val showHiddenFiles: Boolean,
    val mediaFilter: String
)

internal fun WokaSettings.folderSettings(path: String): FolderSettings = FolderSettings(
    layout = gridLayout,
    sort = folderSorts[path] ?: SortType.NameAsc,
    showTitles = folderShowTitles[path] ?: showTitles,
    showFolders = showFolders,
    showHiddenFiles = showHiddenFiles,
    mediaFilter = mediaFilter
)

private val foldersFirst = compareBy<MediaItem> { if (it is MediaItem.Folder) 0 else 1 }

/**
 * One comparator instead of a sort followed by a second stable "folders first"
 * sort — the old pair copied the whole list twice (§10.8). The dates come off
 * [MediaItem.lastModified], captured at listing time, so no syscall runs here.
 */
private fun itemComparator(sort: SortType): Comparator<MediaItem> = when (sort) {
    SortType.NameAsc, SortType.Custom -> foldersFirst.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    SortType.NameDesc -> foldersFirst.thenByDescending(String.CASE_INSENSITIVE_ORDER) { it.name }
    SortType.DateAsc -> foldersFirst.thenBy { it.lastModified }
    SortType.DateDesc -> foldersFirst.thenByDescending { it.lastModified }
}

/**
 * Derives the folder's visible state from the raw listing plus settings, split
 * out of [FolderViewModel] per the §2 file-size mandate. Pure, so the ViewModel
 * is left holding only the flow wiring.
 */
internal fun buildFolderState(
    rawItems: List<MediaItem>,
    settings: FolderSettings,
    isSelectMode: Boolean,
    selectedPaths: Set<String>
): FolderState {
    val visibleItems = if (settings.showHiddenFiles) {
        rawItems
    } else {
        rawItems.filter { !it.isHidden }
    }

    // Filtering on the item type, not on the extension again: the listing already
    // resolved every file through `mediaKind()`, so an image IS a MediaItem.Image.
    val filteredItems = when (settings.mediaFilter) {
        "Photos" -> visibleItems.filter { it is MediaItem.Folder || it is MediaItem.Image }
        "Videos" -> visibleItems.filter { it is MediaItem.Folder || it is MediaItem.Video }
        else -> visibleItems
    }

    return FolderState(
        items = filteredItems.sortedWith(itemComparator(settings.sort)),
        layout = settings.layout,
        sort = settings.sort,
        showTitles = settings.showTitles,
        showFolders = settings.showFolders,
        mediaFilter = settings.mediaFilter,
        isSelectMode = isSelectMode,
        selectedPaths = selectedPaths,
        isMosaic = visibleItems.isMostlyPhotos()
    )
}
