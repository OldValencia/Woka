package com.woka.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import com.woka.app.AppDependencies
import com.woka.core.data.ViewLayout
import com.woka.core.ui.components.ContextMenuPopup
import com.woka.core.ui.components.DropdownItem
import com.woka.core.ui.components.ViewDensityMenu
import com.woka.core.ui.theme.Theme
import com.woka.core.utils.openDirectoryPicker

/**
 * Where the background menu was invoked, and whether it is up. The same shape as
 * `FolderOverlayState`'s half of this, kept apart because Home shares none of that
 * screen's dialogs.
 */
@Stable
class HomeMenuState {
    var isOpen by mutableStateOf(false)
        private set
    var offset by mutableStateOf(IntOffset.Zero)
        private set

    /** Position and visibility are set together, or the menu opens where it last was. */
    fun open(at: IntOffset) {
        offset = at
        isOpen = true
    }

    fun dismiss() {
        isOpen = false
    }
}

/**
 * Right-click on Home's empty space.
 *
 * It is not the folder screen's menu with items removed: "Create folder" and "Pin to
 * Library" are questions about a directory you are standing in, and Home is not one.
 * What takes their place is the only thing Home can be asked to make — a link — and
 * it runs the very same picker the dock's "+" does.
 */
@Composable
internal fun HomeContextMenu(
    menu: HomeMenuState,
    layout: ViewLayout,
    onLayoutChange: (ViewLayout) -> Unit
) {
    if (!menu.isOpen) return

    ContextMenuPopup(offset = menu.offset, onDismiss = { menu.dismiss() }) {
        DropdownItem("Link folder", textColor = Theme.colors.accentHome) {
            menu.dismiss()
            val path = openDirectoryPicker("Link folder")
            if (path != null) AppDependencies.settingsRepository.addLinkedFolder(path)
        }
        ViewDensityMenu(
            layout = layout,
            onSelect = onLayoutChange,
            onDismiss = { menu.dismiss() }
        )
    }
}
