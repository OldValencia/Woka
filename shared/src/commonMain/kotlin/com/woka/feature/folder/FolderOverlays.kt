package com.woka.feature.folder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.woka.core.data.SettingsRepository
import com.woka.core.ui.theme.Theme
import kotlinx.coroutines.launch
import java.io.File
import com.woka.core.ui.components.ContextMenuPopup
import com.woka.core.ui.components.DropdownItem
import com.woka.core.ui.components.RenameDialog
import com.woka.core.ui.components.ViewDensityMenu

/**
 * The folder screen's dialogs and background context menu. State and the eight
 * callbacks that used to carry it are folded into [FolderOverlayState]; the two
 * confirm paths call the ViewModel directly, as `FolderGrid` already does.
 */
@Composable
fun FolderOverlays(
    overlays: FolderOverlayState,
    viewModel: FolderViewModel,
    path: String,
    zoom: FolderZoom,
    settingsRepo: SettingsRepository
) {
    overlays.itemToRename?.let { renamePath ->
        val file = File(renamePath)
        val ext = if (file.isDirectory || file.nameWithoutExtension.isEmpty()) "" else file.extension
        val nameWithoutExt = if (ext.isNotEmpty()) file.nameWithoutExtension else file.name
        RenameDialog(
            initialName = nameWithoutExt,
            onDismiss = { overlays.itemToRename = null },
            onConfirm = { newName ->
                val finalName = if (ext.isNotEmpty()) "$newName.$ext" else newName
                viewModel.renameFile(renamePath, finalName)
                overlays.itemToRename = null
            }
        )
    }

    overlays.itemToRevert?.let { revertPath ->
        val scope = rememberCoroutineScope()
        RevertEditDialog(
            onRevert = {
                scope.launch { revertEdit(revertPath) }
                overlays.itemToRevert = null
            },
            onKeepEdit = { overlays.itemToRevert = null }
        )
    }

    if (overlays.showCreateFolderDialog) {
        RenameDialog(
            initialName = "New Folder",
            title = "Create folder",
            onDismiss = { overlays.showCreateFolderDialog = false },
            onConfirm = { newName ->
                viewModel.createFolder(newName)
                overlays.showCreateFolderDialog = false
            }
        )
    }

    if (overlays.showBgContextMenu) {
        ContextMenuPopup(
            offset = overlays.bgContextMenuOffset,
            onDismiss = { overlays.dismissBgContextMenu() }
        ) {
            DropdownItem("Create folder") {
                overlays.showCreateFolderDialog = true
                overlays.dismissBgContextMenu()
            }
            DropdownItem("Pin to Library", textColor = Theme.colors.accentFolders) {
                viewModel.pinFolder(path)
                overlays.dismissBgContextMenu()
            }
            ViewDensityMenu(
                layout = zoom.layout,
                onSelect = { next -> if (next != zoom.layout) settingsRepo.setGridLayout(next) },
                onDismiss = { overlays.dismissBgContextMenu() }
            )
        }
    }
}
