package com.woka.feature.folder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import com.woka.core.data.SettingsRepository
import com.woka.core.data.ViewLayout
import com.woka.core.ui.components.gridColumnSize
import com.woka.core.ui.components.zoomedIn
import com.woka.core.ui.components.zoomedOut

/**
 * The grid's zoom level and the two ways to change it. The dock accessory needs
 * [layout] for its indicator and the grid needs [columnSize] for its cells, but
 * they are one setting — passing the three separately meant every consumer took
 * three parameters and the `layout → columnSize` mapping lived apart from both.
 */
@Stable
class FolderZoom(
    val layout: ViewLayout,
    val zoomIn: () -> Unit,
    val zoomOut: () -> Unit
) {
    // The numbers themselves moved to `gridColumnSize` when Home gained the same
    // three steps in its top bar: two screens agreeing on "M" and disagreeing on
    // what M is would be a bug nobody would look for here.
    val columnSize: Dp
        get() = layout.gridColumnSize
}

@Composable
internal fun rememberFolderZoom(
    layout: ViewLayout,
    path: String,
    settingsRepo: SettingsRepository
): FolderZoom = remember(layout, path, settingsRepo) {
    FolderZoom(
        layout = layout,
        zoomIn = {
            val next = layout.zoomedIn()
            if (next != layout) settingsRepo.setFolderLayout(path, next)
        },
        zoomOut = {
            val next = layout.zoomedOut()
            if (next != layout) settingsRepo.setFolderLayout(path, next)
        }
    )
}
