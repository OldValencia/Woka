package com.woka.feature.folder

import androidx.compose.runtime.Composable
import com.woka.core.data.MediaItem
import com.woka.core.data.WatchRecord
import com.woka.core.data.mayHoldFrames
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
    dismiss: () -> Unit,
    watch: WatchRecord? = null
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

    if (item is MediaItem.Video) {
        WatchedMenuItem(target, watch, viewModel, dismiss)
    }

    // Only for the formats that can hold more than one picture — on a JPEG it
    // would be a "convert to PNG" hiding under the wrong name.
    if (item.file.mayHoldFrames) {
        DropdownItem("Export Frames...") {
            dismiss()
            val dest = openDirectoryPicker("Export frames to...", path)
            if (dest != null) viewModel.exportFrames(target, dest)
        }
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

/**
 * Asserting watched state by hand, for a video finished somewhere else or one the
 * viewer has no intention of opening.
 *
 * It writes exactly the record a full playthrough writes — one state reached two
 * ways — so the card cannot tell, and has no reason to. The entry inverts once a
 * record exists: being able to claim a state without being able to withdraw it
 * makes a mis-click permanent.
 */
@Composable
private fun WatchedMenuItem(
    target: String,
    watch: WatchRecord?,
    viewModel: FolderViewModel,
    dismiss: () -> Unit
) {
    if (watch?.finished == true) {
        DropdownItem("Mark as not watched") { dismiss(); viewModel.markUnwatched(target) }
    } else {
        DropdownItem("Mark as watched") { dismiss(); viewModel.markWatched(target) }
    }
}
