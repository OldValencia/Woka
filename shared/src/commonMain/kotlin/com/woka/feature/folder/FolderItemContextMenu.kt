package com.woka.feature.folder

import androidx.compose.runtime.Composable
import com.woka.core.data.MediaItem
import com.woka.core.ui.components.DropdownItem
import com.woka.core.ui.theme.Theme
import com.woka.core.utils.copyFilesToClipboard
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
    // Copy means the clipboard, the way it does everywhere else on the desktop;
    // copying into a chosen folder is Duplicate below (TC-SYS-06).
    DropdownItem("Copy") {
        dismiss()
        copyFilesToClipboard(listOf(target))
    }
    DropdownItem("Duplicate") {
        dismiss()
        val dest = openDirectoryPicker("Duplicate to...", path)
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
