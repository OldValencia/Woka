package com.woka.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.woka.app.shell.sidebar.Sidebar
import com.woka.core.ui.theme.Theme

/**
 * The window: the sidebar on the left at full height, and the toolbar over the
 * content beside it. There is no dock — SIDE-003 moved everything it carried into
 * the toolbar and the grid's own selection bar.
 */
@Composable
fun MainShell(
    topBarArea: @Composable (content: @Composable () -> Unit) -> Unit,
    currentPath: String?,
    onCloseWindow: () -> Unit,
    onMinimizeWindow: () -> Unit,
    onMaximizeWindow: () -> Unit,
    isMaximized: Boolean = false,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    content: @Composable () -> Unit
) {
    val topBarAccessories = remember { TopBarAccessoriesState() }
    val globalShortcuts = remember { GlobalShortcutState() }
    val canvasOverride = remember { CanvasOverrideState() }
    val openSearch by rememberUpdatedState(onOpenSearch)
    DisposableEffect(Unit) {
        WindowKeys.handler = { shellKey(it, openSearch) }
        onDispose { WindowKeys.handler = null }
    }

    CompositionLocalProvider(
        LocalTopBarAccessories provides topBarAccessories,
        LocalGlobalShortcuts provides globalShortcuts,
        LocalCanvasOverride provides canvasOverride
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Theme.colors.background)
                .fileDrops()
                // Runs in the preview (tunneling) phase from the root, so a
                // registered screen shortcut sees keys before any focused button
                // and can consume them (see GlobalShortcutState). The shell's own
                // keys are the window's, not this node's — see `WindowKeys`.
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
                            onOpenSettings = onOpenSettings,
                            isMaximized = isMaximized
                        )
                    }

                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        content()
                    }
                }
            }
            DropOverlay()
        }
    }
}

/** Ctrl+F opens search, the one key the shell answers itself (SIDE-003). */
private fun shellKey(event: KeyEvent, openSearch: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed || event.key != Key.F) return false
    openSearch()
    return true
}
