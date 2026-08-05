package com.woka.feature.folder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.woka.app.shell.LocalDockAccessories
import com.woka.core.data.PinnedSession
import com.woka.core.data.SettingsRepository
import com.woka.core.data.ViewLayout
import com.woka.core.utils.openDirectoryPicker
import com.woka.feature.library.LibraryTopAccessory

@Composable
fun FolderDockSetup(
    path: String,
    state: FolderState,
    isPinned: Boolean,
    layout: ViewLayout,
    gridScrollIndex: Int,
    gridScrollOffset: Int,
    viewModel: FolderViewModel,
    settingsRepo: SettingsRepository,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
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
                    else settingsRepo.addPinnedSession(PinnedSession(path, gridScrollIndex, gridScrollOffset))
                },
                onToggleSelectMode = { viewModel.toggleSelectMode() },
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
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
}
