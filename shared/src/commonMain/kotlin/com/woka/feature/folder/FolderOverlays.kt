package com.woka.feature.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.woka.core.ui.theme.Theme
import java.io.File
import com.woka.core.ui.components.DropdownItem
import com.woka.core.ui.components.RenameDialog

@Composable
fun FolderOverlays(
    itemToRename: String?,
    showCreateFolderDialog: Boolean,
    showBgContextMenu: Boolean,
    bgContextMenuOffset: IntOffset,
    onDismissRename: () -> Unit,
    onConfirmRename: (String, String) -> Unit, // path, new name
    onDismissCreateFolder: () -> Unit,
    onConfirmCreateFolder: (String) -> Unit,
    onDismissBgContextMenu: () -> Unit,
    onCreateFolderClick: () -> Unit,
    onPinFolderClick: () -> Unit
) {
    itemToRename?.let { renamePath ->
        val file = File(renamePath)
        val ext = if (file.isDirectory || file.nameWithoutExtension.isEmpty()) "" else file.extension
        val nameWithoutExt = if (ext.isNotEmpty()) file.nameWithoutExtension else file.name
        RenameDialog(
            initialName = nameWithoutExt,
            onDismiss = onDismissRename,
            onConfirm = { newName ->
                val finalName = if (ext.isNotEmpty()) "$newName.$ext" else newName
                onConfirmRename(renamePath, finalName)
            }
        )
    }

    if (showCreateFolderDialog) {
        RenameDialog(
            initialName = "New Folder",
            title = "Create folder",
            onDismiss = onDismissCreateFolder,
            onConfirm = onConfirmCreateFolder
        )
    }

    if (showBgContextMenu) {
        Popup(
            alignment = Alignment.TopStart,
            offset = bgContextMenuOffset,
            onDismissRequest = onDismissBgContextMenu
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
                    onCreateFolderClick()
                }
                DropdownItem("Pin to Library", textColor = Theme.colors.accentFolders) {
                    onPinFolderClick()
                }
            }
        }
    }
}
