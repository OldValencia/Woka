package com.woka.core.ui.components

import androidx.compose.runtime.Composable
import com.woka.core.utils.copyTextToClipboard
import com.woka.core.utils.fileManagerName
import com.woka.core.utils.openInFileManager
import java.io.File

/**
 * The two entries every place that names a folder offers — the folder's background menu,
 * a sidebar row and the toolbar title: open it in the system's file manager, and copy its
 * full path as text, `C:/Users/…` rather than `C:\Users\…`. One composable so the three
 * menus cannot drift apart.
 *
 * [onDone] closes the menu, and runs first, as every other entry's dismiss does.
 */
@Composable
fun FolderPathItems(path: String, onDone: () -> Unit) {
    DropdownItem("Open in $fileManagerName") {
        onDone()
        openInFileManager(path)
    }
    DropdownItem("Copy Path") {
        onDone()
        // Forward slashes on Windows too (the author, 2026-11-04): Explorer, Win+R,
        // PowerShell and every file dialog read them, and they read like a path.
        copyTextToClipboard(File(path).absolutePath.replace('\\', '/'))
    }
}
