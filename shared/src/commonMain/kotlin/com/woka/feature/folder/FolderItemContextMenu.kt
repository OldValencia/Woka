package com.woka.feature.folder

import androidx.compose.runtime.Composable
import com.woka.core.data.MediaItem
import com.woka.core.ui.components.DropdownItem
import com.woka.core.ui.theme.Theme
import com.woka.core.utils.openDirectoryPicker

/**
 * Right-click menu for a single grid tile. Split out of [FolderGrid] (§11): what
 * you can do *to* an item is a different concern from how the grid lays items out,
 * and it was the one block making that function unreadable.
 *
 * [dismiss] is called before every action so the popup is gone before a blocking
 * native picker opens.
 */
@Composable
internal fun FolderItemContextMenu(
    item: MediaItem,
    path: String,
    viewModel: FolderViewModel,
    overlays: FolderOverlayState,
    onNavigateToFolder: (String) -> Unit,
    onOpenMedia: (String) -> Unit,
    dismiss: () -> Unit
) {
    val target = item.file.absolutePath

    if (item is MediaItem.Folder) {
        DropdownItem("Open") { dismiss(); onNavigateToFolder(target) }
        DropdownItem("Pin to Library", textColor = Theme.colors.accentFolders) {
            dismiss()
            viewModel.pinFolder(target)
        }
    } else {
        DropdownItem("Open") { dismiss(); onOpenMedia(target) }
    }

    DropdownItem("Rename") { dismiss(); overlays.itemToRename = target }
    DropdownItem("Copy") {
        dismiss()
        val dest = openDirectoryPicker("Copy to...", path)
        if (dest != null) viewModel.copyFiles(listOf(target), dest)
    }
    DropdownItem("Move") {
        dismiss()
        val dest = openDirectoryPicker("Move to...", path)
        if (dest != null) viewModel.moveFiles(listOf(target), dest)
    }
    DropdownItem("Delete", textColor = Theme.colors.danger) {
        dismiss()
        viewModel.deleteFiles(listOf(target))
    }
}
