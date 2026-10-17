package com.woka.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.woka.app.shell.sidebar.Sidebar
import com.woka.core.ui.theme.Theme

@Composable
fun MainShell(
    topBarArea: @Composable (content: @Composable () -> Unit) -> Unit,
    currentPath: String?,
    activeTab: DockTab?,
    onTabSelected: (DockTab) -> Unit,
    onCloseWindow: () -> Unit,
    onMinimizeWindow: () -> Unit,
    onMaximizeWindow: () -> Unit,
    isMaximized: Boolean = false,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onNavigateBack: (() -> Unit)?,
    onBreadcrumbClick: ((String) -> Unit)?,
    linkedFolders: List<String> = emptyList(),
    content: @Composable () -> Unit
) {
    val dockAccessories = remember { DockAccessoriesState() }
    val topBarAccessories = remember { TopBarAccessoriesState() }
    val globalShortcuts = remember { GlobalShortcutState() }
    val canvasOverride = remember { CanvasOverrideState() }

    CompositionLocalProvider(
        LocalDockAccessories provides dockAccessories,
        LocalTopBarAccessories provides topBarAccessories,
        LocalGlobalShortcuts provides globalShortcuts,
        LocalCanvasOverride provides canvasOverride
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Theme.colors.background)
                // Runs in the preview (tunneling) phase from the root, so a
                // registered screen shortcut sees keys before any focused button
                // and can consume them (see GlobalShortcutState).
                .onPreviewKeyEvent { globalShortcuts.onPreviewKey?.invoke(it) ?: false }
        ) {
            // The sidebar spans the full height, top bar included, so its header
            // shares the title bar's row and drags the window the same way (SIDE-001).
            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(currentPath = currentPath, topBarArea = topBarArea)

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    topBarArea {
                        GlobalTopBar(
                            currentPath = currentPath,
                            onClose = onCloseWindow,
                            onMinimize = onMinimizeWindow,
                            onMaximize = onMaximizeWindow,
                            onNavigateBack = onNavigateBack,
                            onBreadcrumbClick = onBreadcrumbClick,
                            linkedFolders = linkedFolders,
                            isMaximized = isMaximized
                        )
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        content()

                        DockLayer(
                            dockAccessories = dockAccessories,
                            activeTab = activeTab,
                            onTabSelected = onTabSelected,
                            onOpenSearch = onOpenSearch,
                            onOpenSettings = onOpenSettings
                        )
                    }
                }
            }
        }
    }
}
