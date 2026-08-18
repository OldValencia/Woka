package com.woka.feature.folder

import androidx.compose.runtime.Composable
import com.woka.core.data.MediaItem
import com.woka.core.ui.components.FolderCard
import com.woka.core.ui.components.WatchMarks

/**
 * One tile of the grid: which card, wired to which actions.
 *
 * Split out of [FolderGrid] under the §2 file-size mandate when the watched marks
 * arrived, following the same line [FolderItemContextMenu] was drawn along — the
 * grid lays items out, this says what an item *is*.
 */
@Composable
internal fun FolderGridItem(
    item: MediaItem,
    state: FolderState,
    viewModel: FolderViewModel,
    marks: WatchMarks,
    path: String,
    overlays: FolderOverlayState,
    onNavigateToFolder: (String) -> Unit,
    onOpenMedia: (String) -> Unit,
    onEditMedia: (String) -> Unit,
    highlightFile: String?
) {
    val target = item.file.absolutePath
    val isDir = item is MediaItem.Folder
    val watch = marks.forVideo(item)

    FolderCard(
        path = target,
        onClick = {
            if (state.isSelectMode) {
                viewModel.toggleSelection(target)
            } else {
                if (isDir) onNavigateToFolder(target) else onOpenMedia(target)
            }
        },
        showTitle = state.showTitles,
        isSelected = state.selectedPaths.contains(target),
        isHighlighted = highlightFile == item.file.name,
        onModifierClick = { isAlt, isShift ->
            if (isShift) viewModel.selectRange(target) else if (isAlt) viewModel.toggleSelection(target)
        },
        contextMenuContent = { dismiss ->
            FolderItemContextMenu(
                item = item,
                path = path,
                viewModel = viewModel,
                overlays = overlays,
                onNavigateToFolder = onNavigateToFolder,
                onOpenMedia = onOpenMedia,
                onEditMedia = onEditMedia,
                dismiss = dismiss,
                watch = watch
            )
        },
        watch = watch,
        dimWatched = marks.dimStarted
    )
}
