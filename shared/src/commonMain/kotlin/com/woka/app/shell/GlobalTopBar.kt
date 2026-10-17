package com.woka.app.shell

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.woka.app.AppDependencies
import com.woka.app.collectField
import com.woka.core.ui.icons.Icons
import com.woka.feature.folder.InfoPanel

/** Under this width the search box shrinks to its icon (SIDE-003). */
private val COMPACT_BELOW = 620.dp

/**
 * The window's toolbar (SIDE-003, `sidebar-redesign.html` `.bar`): 52.dp, right of
 * the sidebar.
 *
 * - **Left**, from the edge in:
 *   - the show-sidebar button while the sidebar is hidden;
 *   - the overlay's `<`;
 *   - the folder's name, or a screen's leading accessory.
 *
 * **The name, not the path** (2026-10-17). A breadcrumb trail took the bar's width
 * in a deep folder and said what the sidebar's tree already shows, opened down to
 * this folder and highlighted.
 * - **Centre**: an overlay's controls.
 * - **Right**: the screen's controls, then Search and Settings, then the window
 *   controls.
 *
 * **No Back for history.** It went with the dock: the tree and the path are the
 * navigation. The `<` drawn here is only an overlay's way out — the viewer and the
 * editor set [TopBarAccessoriesState.backActionOverride] — which a file launched
 * from Explorer, on a window with nothing else in it, still needs.
 *
 * Search and Settings step aside while an overlay holds the centre, as the dock did
 * under the viewer: the viewer and the editor speak for the whole bar.
 */
@Composable
fun GlobalTopBar(
    currentPath: String?,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onOpenSettings: () -> Unit,
    isMaximized: Boolean = false,
    modifier: Modifier = Modifier
) {
    val topBarAccessories = LocalTopBarAccessories.current
    val settingsRepo = remember { AppDependencies.settingsRepository }
    val sidebarVisible by settingsRepo.collectField { it.sidebarVisible }
    val overlay = topBarAccessories.centerAccessory != null

    // Window drag, drag-to-restore and double-click maximize are handled by the
    // platform wrapper around the top bar (see desktop main.kt), not here.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 10.dp)
    ) {
        CompositionLocalProvider(LocalToolbarCompact provides (maxWidth < COMPACT_BELOW)) {
            // One row, the path taking what the right side leaves and clipping there:
            // the right side is laid out first at its own width, so the two can never
            // overlap (2026-10-17 — two independently aligned rows did, the filter
            // running over the path).
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f).clipToBounds(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!sidebarVisible) {
                        ToolbarSidebarButton(
                            "Show sidebar",
                            onClick = { settingsRepo.setSidebarVisible(true) }
                        )
                    }
                    topBarAccessories.backActionOverride?.let { back ->
                        ToolbarIconButton(Icons.Static.ChevronLeft, "Back", onClick = back)
                    }
                    // The folder's name and a screen's leading accessory are one slot:
                    // both answer "what is this screen". The name yields to an overlay
                    // that has taken the centre; the editor's Revert wants the slot then.
                    if (currentPath != null && !overlay) {
                        ToolbarTitle(currentPath)
                    } else {
                        topBarAccessories.leadingAccessory?.invoke()
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    topBarAccessories.rightAccessory?.invoke()
                    if (!overlay) {
                        ToolbarSearchField()
                        if (currentPath != null) {
                            ToolbarInfoButton(InfoPanel.open, onClick = { InfoPanel.open = !InfoPanel.open })
                        }
                        ToolbarIconButton(Icons.Static.Settings, "Settings", onClick = onOpenSettings)
                    }
                    Spacer(Modifier.width(4.dp))
                    WinControls(
                        onClose = onClose,
                        onMinimize = onMinimize,
                        onMaximize = onMaximize,
                        isMaximized = isMaximized
                    )
                }
            }

            // A Row since ME-173: the pair is centred over the bar, so an editor
            // control can sit beside the viewer's pill without being composed inside it.
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                topBarAccessories.centerAccessory?.invoke()
                topBarAccessories.centerTrailingAccessory?.invoke()
            }
        }
    }
}
