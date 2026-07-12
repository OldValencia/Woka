package io.iris.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.iris.AppDependencies
import io.iris.data.PinnedSession
import io.iris.data.ViewLayout
import io.iris.ui.FolderState
import io.iris.ui.FolderViewModel
import io.iris.utils.openDirectoryPicker

@Composable
fun FolderContent(
    path: String,
    state: FolderState,
    viewModel: FolderViewModel,
    onNavigateToFolder: (String) -> Unit,
    itemToRename: String?,
    showCreateFolderDialog: Boolean,
    showBgContextMenu: Boolean,
    bgContextMenuOffset: IntOffset,
    onRenameItemChange: (String?) -> Unit,
    onShowCreateFolderDialogChange: (Boolean) -> Unit,
    onShowBgContextMenuChange: (Boolean) -> Unit,
    onBgContextMenuOffsetChange: (IntOffset) -> Unit
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
                settingsRepo.addPinnedSession(PinnedSession(path, gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset))
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

    Column(modifier = Modifier.fillMaxSize()) {
    val dockAccessories = LocalDockAccessories.current
    DisposableEffect(state, isPinned, layout, state.sort, path) {
        val dockKey = "folder_$path"
        dockAccessories.setLeft(dockKey) {
            LibraryLeftAccessory(
                isPinned = isPinned,
                isSelectMode = state.isSelectMode,
                layout = layout,
                sort = state.sort,
                onTogglePin = {
                    if (isPinned) settingsRepo.removePinnedSession(path)
                    else settingsRepo.addPinnedSession(PinnedSession(path, gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset))
                },
                onToggleSelectMode = { viewModel.toggleSelectMode() },
                onZoomIn = zoomIn,
                onZoomOut = zoomOut,
                onSortChange = { viewModel.setSort(it) }
            )
        }
        
        if (state.isSelectMode) {
            dockAccessories.setTop(dockKey) {
                LibraryTopAccessory(
                    selectedCount = state.selectedPaths.size,
                    onSelectAll = { viewModel.selectAll() },
                    onDuplicate = {
                        if (state.selectedPaths.isNotEmpty()) {
                            viewModel.duplicateFiles(state.selectedPaths.toList())
                            viewModel.toggleSelectMode()
                        }
                    },
                    onCopy = {
                        if (state.selectedPaths.isNotEmpty()) {
                            val dest = openDirectoryPicker("Copy to...", path)
                            if (dest != null) {
                                viewModel.copyFiles(state.selectedPaths.toList(), dest)
                                viewModel.toggleSelectMode()
                            }
                        }
                    },
                    onMove = {
                        if (state.selectedPaths.isNotEmpty()) {
                            val dest = openDirectoryPicker("Move to...", path)
                            if (dest != null) {
                                viewModel.moveFiles(state.selectedPaths.toList(), dest)
                                viewModel.toggleSelectMode()
                            }
                        }
                    },
                    onDelete = {
                        if (state.selectedPaths.isNotEmpty()) {
                            viewModel.deleteFiles(state.selectedPaths.toList())
                            viewModel.toggleSelectMode()
                        }
                    }
                )
            }
        } else {
            dockAccessories.clearTop(dockKey)
        }
        
        onDispose { 
            dockAccessories.clearLeft(dockKey)
            dockAccessories.clearTop(dockKey)
        }
    }

        FolderGrid(
            state = state,
            viewModel = viewModel,
            gridState = gridState,
            gridColumnSize = gridColumnSize,
            path = path,
            focusRequester = focusRequester,
            onNavigateToFolder = onNavigateToFolder,
            onShowBgContextMenu = { offset ->
                onBgContextMenuOffsetChange(offset)
                onShowBgContextMenuChange(true)
            },
            onZoomIn = zoomIn,
            onZoomOut = zoomOut,
            onRenameItem = { onRenameItemChange(it) },
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
