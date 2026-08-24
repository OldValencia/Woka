package com.woka.feature.home

import androidx.compose.runtime.Composable
import com.woka.app.AppDependencies
import com.woka.app.shell.DockSecondaryItem
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.core.utils.openDirectoryPicker

/**
 * "Link a folder", now the dock's first item instead of a round island floating
 * beside it. One panel at the bottom of the window rather than two objects at the
 * same level, and the button inherits the dock's own hover behaviour for free.
 */
@Composable
internal fun HomeAddButton() {
    DockSecondaryItem(
        iconRes = Icons.Static.Add,
        hoverColor = Theme.colors.accentHome,
        onClick = {
            val path = openDirectoryPicker("Link folder")
            if (path != null) AppDependencies.settingsRepository.addLinkedFolder(path)
        }
    )
}
