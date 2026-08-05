package com.woka.feature.folder

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset

/**
 * The folder screen's transient UI: which dialog is open and where the background
 * context menu was invoked.
 *
 * These four values plus their four setters were eight of `FolderContent`'s
 * sixteen parameters, threaded down from `FolderScreen` and back up again through
 * `onShow…Change` callbacks. They are one thing with one owner, so they travel as
 * one — the same shape as `MediaViewerState` and `PopoverHoverState`.
 */
@Stable
class FolderOverlayState {
    var itemToRename by mutableStateOf<String?>(null)
    var showCreateFolderDialog by mutableStateOf(false)
    var showBgContextMenu by mutableStateOf(false)
        private set
    var bgContextMenuOffset by mutableStateOf(IntOffset.Zero)
        private set

    /** Position and visibility are set together, or the menu opens where it last was. */
    fun openBgContextMenu(at: IntOffset) {
        bgContextMenuOffset = at
        showBgContextMenu = true
    }

    fun dismissBgContextMenu() {
        showBgContextMenu = false
    }
}
