package com.woka.feature.folder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.woka.app.shell.LocalDockAccessories
import com.woka.core.data.PinnedSession
import com.woka.core.data.SettingsRepository
import com.woka.core.utils.openDirectoryPicker
import com.woka.feature.library.LibraryTopAccessory
import java.io.File

@Composable
fun FolderDockSetup(
    path: String,
    state: FolderState,
    isPinned: Boolean,
    zoom: FolderZoom,
    gridScrollIndex: Int,
    gridScrollOffset: Int,
    viewModel: FolderViewModel,
    settingsRepo: SettingsRepository,
    showPinOffer: Boolean = false,
    onPinOfferResolved: () -> Unit = {}
) {
    val dockAccessories = LocalDockAccessories.current
    val resolvePinOffer by rememberUpdatedState(onPinOfferResolved)
    DisposableEffect(state, isPinned, zoom, state.sort, path, showPinOffer) {
        val dockKey = "folder_$path"
        dockAccessories.setLeft(dockKey) {
            LibraryLeftAccessory(
                isPinned = isPinned,
                isSelectMode = state.isSelectMode,
                layout = zoom.layout,
                sort = state.sort,
                onTogglePin = {
                    if (isPinned) settingsRepo.removePinnedSession(path)
                    else settingsRepo.addPinnedSession(PinnedSession(path, gridScrollIndex, gridScrollOffset))
                },
                onToggleSelectMode = { viewModel.toggleSelectMode() },
                onZoomIn = zoom.zoomIn,
                onZoomOut = zoom.zoomOut,
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
        } else if (showPinOffer) {
            // Select mode wins the slot: it is a state the user is standing in,
            // the offer is one they can take at any point afterwards.
            dockAccessories.setTop(dockKey) {
                FolderPinPrompt(
                    folderName = File(path).name,
                    onPin = {
                        settingsRepo.addLinkedFolder(path)
                        resolvePinOffer()
                    },
                    onDismiss = { resolvePinOffer() }
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
