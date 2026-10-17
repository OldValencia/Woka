package com.woka.feature.folder

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.woka.core.data.MediaItem
import com.woka.feature.viewer.VideoHoverExcerpts
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
    highlightFile: String?,
    focus: FolderFocus,
    tile: Boolean = false
) {
    val target = item.file.absolutePath
    val isDir = item is MediaItem.Folder
    val watch = marks.forVideo(item)

    FolderCard(
        path = target,
        onClick = {
            focus.focused = target
            if (state.isSelectMode) {
                viewModel.toggleSelection(target)
            } else {
                if (isDir) onNavigateToFolder(target) else onOpenMedia(target)
            }
        },
        showTitle = state.showTitles,
        isSelected = state.selectedPaths.contains(target),
        isHighlighted = highlightFile == item.file.name,
        // Selecting an item focuses it too (2026-10-17): Space previews the focused
        // item, and a selection made by the circle or a modifier-click left the focus
        // on whatever was clicked before, so Quick Look showed some other picture.
        onModifierClick = { isAlt, isShift ->
            focus.focused = target
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
        dimWatched = marks.dimStarted,
        tile = tile,
        onCheck = {
            focus.focused = target
            viewModel.toggleSelection(target)
        },
        isFocused = focus.focused == target,
        onHoverChange = { over ->
            if (over) focus.hovered = target else if (focus.hovered == target) focus.hovered = null
        },
        coverOverlay = if (item is MediaItem.Video) {
            { hovered -> if (hovered) VideoHoverExcerpts(item.file, Modifier.fillMaxSize()) }
        } else null
    )
}
