package com.woka.feature.folder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.woka.core.data.MediaItem
import com.woka.core.data.WatchRecord
import com.woka.core.data.mayHoldFrames
import com.woka.core.ui.components.DropdownItem
import com.woka.core.ui.theme.Theme
import com.woka.core.utils.copyFilesToClipboard
import com.woka.core.utils.copyImageToClipboard
import com.woka.core.utils.openDirectoryPicker
import com.woka.feature.editor.VIDEO_EDITING_ENABLED
import com.woka.feature.editor.core.EditorEngineFactory
import com.woka.feature.editor.isEditable
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The one thing this menu needs from outside the folder feature: the single answer to
 * "is this editable" (§4.1). Held as an object rather than injected per item, because a
 * grid draws a menu per right-click and the factory is a `single` either way.
 */
internal object EditorMenuDependencies : KoinComponent {
    val factory: EditorEngineFactory by inject()
}

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
    onEditMedia: (String) -> Unit,
    dismiss: () -> Unit,
    watch: WatchRecord? = null
) {
    val target = item.file.absolutePath
    val engineFactory = EditorMenuDependencies.factory
    // The decode a picture copy needs; the menu is gone by the time it finishes.
    val scope = rememberCoroutineScope()

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

    // Beside Rename/Copy/Delete, where §4.1 puts it. Greyed for anything the engine
    // factory refuses — video until ME-201, and every format Skia cannot encode — so
    // the entry is honest rather than absent.
    if (item !is MediaItem.Folder && (item !is MediaItem.Video || VIDEO_EDITING_ENABLED)) {
        DropdownItem("Edit", enabled = isEditable(engineFactory, target)) {
            dismiss()
            onEditMedia(target)
        }
    }
    DropdownItem("Rename") { dismiss(); overlays.itemToRename = target }
    // **Copy is the picture**, not the file (2026-08-19). Putting the file on the
    // clipboard made this a second Duplicate — and Duplicate is right below. Pasting
    // now gives the photograph as it is drawn, edit folded in. A video has no picture
    // to hand over, so there Copy still means the file.
    DropdownItem("Copy") {
        dismiss()
        if (item is MediaItem.Image) {
            scope.launch { copyImageToClipboard(target) }
        } else {
            copyFilesToClipboard(listOf(target))
        }
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
    // Back to as shot, without opening the editor (ME-107). Greyed when the file
    // carries nothing, so the entry is honest rather than absent — and confirmed,
    // because in this menu it takes effect on the file with no document to undo from.
    if (item is MediaItem.Image) {
        DropdownItem(
            "Revert to Original",
            enabled = rememberCarriesEdit(item.file),
            textColor = Theme.colors.danger
        ) {
            dismiss()
            overlays.itemToRevert = target
        }
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
