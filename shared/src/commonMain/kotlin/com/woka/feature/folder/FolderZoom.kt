package com.woka.feature.folder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.core.data.SettingsRepository
import com.woka.core.data.ViewLayout

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
    val columnSize: Dp
        get() = when (layout) {
            ViewLayout.SmallGrid -> 120.dp
            ViewLayout.Grid -> 240.dp
            ViewLayout.BigGrid -> 400.dp
        }
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
            val next = when (layout) {
                ViewLayout.SmallGrid -> ViewLayout.Grid
                ViewLayout.Grid -> ViewLayout.BigGrid
                ViewLayout.BigGrid -> ViewLayout.BigGrid
            }
            if (next != layout) settingsRepo.setFolderLayout(path, next)
        },
        zoomOut = {
            val next = when (layout) {
                ViewLayout.BigGrid -> ViewLayout.Grid
                ViewLayout.Grid -> ViewLayout.SmallGrid
                ViewLayout.SmallGrid -> ViewLayout.SmallGrid
            }
            if (next != layout) settingsRepo.setFolderLayout(path, next)
        }
    )
}
