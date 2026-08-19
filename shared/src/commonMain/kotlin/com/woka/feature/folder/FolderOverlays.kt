package com.woka.feature.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.woka.core.ui.theme.Theme
import kotlinx.coroutines.launch
import java.io.File
import com.woka.core.ui.components.DropdownItem
import com.woka.core.ui.components.RenameDialog

/**
 * The folder screen's dialogs and background context menu. State and the eight
 * callbacks that used to carry it are folded into [FolderOverlayState]; the two
 * confirm paths call the ViewModel directly, as `FolderGrid` already does.
 */
@Composable
fun FolderOverlays(
    overlays: FolderOverlayState,
    viewModel: FolderViewModel,
    path: String
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
        Popup(
            alignment = Alignment.TopStart,
            offset = overlays.bgContextMenuOffset,
            onDismissRequest = { overlays.dismissBgContextMenu() }
        ) {
            Column(
                modifier = Modifier
                    .width(180.dp)
                    .shadow(12.dp, RoundedCornerShape(10.dp), spotColor = Color.Black.copy(alpha = 0.2f))
                    .background(Theme.colors.surfaceElevated, RoundedCornerShape(10.dp))
                    .border(1.dp, Theme.colors.border, RoundedCornerShape(10.dp))
                    .padding(6.dp)
            ) {
                DropdownItem("Create folder") {
                    overlays.showCreateFolderDialog = true
                    overlays.dismissBgContextMenu()
                }
                DropdownItem("Pin to Library", textColor = Theme.colors.accentFolders) {
                    viewModel.pinFolder(path)
                    overlays.dismissBgContextMenu()
                }
            }
        }
    }
}
