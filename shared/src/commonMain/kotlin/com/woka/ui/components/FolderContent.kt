package com.woka.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.woka.AppDependencies
import com.woka.data.PinnedSession
import com.woka.data.ViewLayout
import com.woka.ui.FolderState
import com.woka.ui.FolderViewModel
import com.woka.ui.createFolder
import com.woka.ui.renameFile

@Composable
fun FolderContent(
    path: String,
    highlightFile: String? = null,
    state: FolderState,
    viewModel: FolderViewModel,
    onNavigateToFolder: (String) -> Unit,
    onOpenMedia: (String) -> Unit,
    itemToRename: String?,
    showCreateFolderDialog: Boolean,
    showBgContextMenu: Boolean,
    bgContextMenuOffset: IntOffset,
    onRenameItemChange: (String?) -> Unit,
    onShowCreateFolderDialogChange: (Boolean) -> Unit,
    onShowBgContextMenuChange: (Boolean) -> Unit,
    onBgContextMenuOffsetChange: (IntOffset) -> Unit,
    scrollToMediaId: String? = null,
    onScrollToMediaIdConsumed: () -> Unit = {}
) {
    val settingsRepo = remember { AppDependencies.settingsRepository }
    val settings by settingsRepo.settings.collectAsState()
    val isPinned = settings.pinnedSessions.any { it.path == path }
    val currentSession = settings.pinnedSessions.find { it.path == path }

    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = currentSession?.scrollIndex ?: 0,
        initialFirstVisibleItemScrollOffset = currentSession?.scrollOffset ?: 0
    )

    DisposableEffect(path) {
        onDispose {
            if (settingsRepo.settings.value.pinnedSessions.any { it.path == path }) {
                settingsRepo.addPinnedSession(
                    PinnedSession(
                        path,
                        gridState.firstVisibleItemIndex,
                        gridState.firstVisibleItemScrollOffset
                    )
                )
            }
        }
    }

    val layout = settings.folderLayouts[path] ?: ViewLayout.Grid
    val gridColumnSize = when (layout) {
        ViewLayout.SmallGrid -> 120.dp
        ViewLayout.Grid -> 240.dp
        ViewLayout.BigGrid -> 400.dp
    }

    val zoomIn = {
        val newLayout = when (layout) {
            ViewLayout.SmallGrid -> ViewLayout.Grid
            ViewLayout.Grid -> ViewLayout.BigGrid
            ViewLayout.BigGrid -> ViewLayout.BigGrid
        }
        if (newLayout != layout) settingsRepo.setFolderLayout(path, newLayout)
    }

    val zoomOut = {
        val newLayout = when (layout) {
            ViewLayout.BigGrid -> ViewLayout.Grid
            ViewLayout.Grid -> ViewLayout.SmallGrid
            ViewLayout.SmallGrid -> ViewLayout.SmallGrid
        }
        if (newLayout != layout) settingsRepo.setFolderLayout(path, newLayout)
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(scrollToMediaId, state.items) {
        if (scrollToMediaId != null && state.items.isNotEmpty()) {
            val index = state.items.indexOfFirst { it.file.absolutePath == scrollToMediaId }
            if (index >= 0) {
                gridState.scrollToItem(index)
            }
            onScrollToMediaIdConsumed()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FolderDockSetup(
            path = path,
            state = state,
            isPinned = isPinned,
            layout = layout,
            gridScrollIndex = gridState.firstVisibleItemIndex,
            gridScrollOffset = gridState.firstVisibleItemScrollOffset,
            viewModel = viewModel,
            settingsRepo = settingsRepo,
            onZoomIn = zoomIn,
            onZoomOut = zoomOut
        )

        FolderGrid(
            state = state,
            viewModel = viewModel,
            gridState = gridState,
            gridColumnSize = gridColumnSize,
            path = path,
            highlightFile = highlightFile,
            focusRequester = focusRequester,
            onNavigateToFolder = onNavigateToFolder,
            onShowBgContextMenu = { offset ->
                onBgContextMenuOffsetChange(offset)
                onShowBgContextMenuChange(true)
            },
            onZoomIn = zoomIn,
            onZoomOut = zoomOut,
            onRenameItem = { onRenameItemChange(it) },
            onOpenMedia = onOpenMedia,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }

    FolderOverlays(
        itemToRename = itemToRename,
        showCreateFolderDialog = showCreateFolderDialog,
        showBgContextMenu = showBgContextMenu,
        bgContextMenuOffset = bgContextMenuOffset,
        onDismissRename = { onRenameItemChange(null) },
        onConfirmRename = { renamePath, finalName ->
            viewModel.renameFile(renamePath, finalName)
            onRenameItemChange(null)
        },
        onDismissCreateFolder = { onShowCreateFolderDialogChange(false) },
        onConfirmCreateFolder = { newName ->
            viewModel.createFolder(newName)
            onShowCreateFolderDialogChange(false)
        },
        onDismissBgContextMenu = { onShowBgContextMenuChange(false) },
        onCreateFolderClick = {
            onShowCreateFolderDialogChange(true)
            onShowBgContextMenuChange(false)
        },
        onPinFolderClick = {
            viewModel.pinFolder(path)
            onShowBgContextMenuChange(false)
        }
    )
}
