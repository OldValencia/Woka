package io.iris.ui.components

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.iris.data.MediaItem
import io.iris.ui.FolderState
import io.iris.ui.FolderViewModel
import io.iris.ui.theme.IrisTheme
import io.iris.utils.openDirectoryPicker

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FolderGrid(
    state: FolderState,
    viewModel: FolderViewModel,
    gridState: LazyGridState,
    gridColumnSize: Dp,
    path: String,
    highlightFile: String? = null,
    focusRequester: FocusRequester,
    onNavigateToFolder: (String) -> Unit,
    onShowBgContextMenu: (IntOffset) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRenameItem: (String) -> Unit,
    onOpenMedia: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.runtime.LaunchedEffect(highlightFile, state.items) {
        if (highlightFile != null && state.items.isNotEmpty()) {
            val index = state.items.indexOfFirst { it.file.name == highlightFile }
            if (index != -1) {
                gridState.animateScrollToItem(index)
            }
        }
    }

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.button == PointerButton.Secondary) {
                    val change = event.changes.first()
                    if (!change.isConsumed) {
                        onShowBgContextMenu(IntOffset(change.position.x.toInt() + 2, change.position.y.toInt() + 2))
                        change.consume()
                    }
                }
            }
            .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { event ->
                if (event.keyboardModifiers.isCtrlPressed) {
                    val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (delta < 0) {
                        onZoomIn()
                    } else if (delta > 0) {
                        onZoomOut()
                    }
                    event.changes.forEach { it.consume() }
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed) {
                    when (event.key) {
                        Key.Equals, Key.Plus, Key.NumPadAdd -> {
                            onZoomIn()
                            true
                        }

                        Key.Minus, Key.NumPadSubtract -> {
                            onZoomOut()
                            true
                        }

                        else -> false
                    }
                } else false
            }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(gridColumnSize),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            contentPadding = PaddingValues(top = 20.dp, start = 40.dp, end = 40.dp, bottom = 120.dp)
        ) {
            items(state.items, key = { it.file.absolutePath }) { item ->
                val isDir = item is MediaItem.Folder
                val isSelected = state.selectedPaths.contains(item.file.absolutePath)
                val onClickAction = {
                    if (state.isSelectMode) {
                        viewModel.toggleSelection(item.file.absolutePath)
                    } else {
                        if (isDir) onNavigateToFolder(item.file.absolutePath)
                        else onOpenMedia(item.file.absolutePath)
                    }
                }
                FolderCard(
                    path = item.file.absolutePath,
                    onClick = onClickAction,
                    showTitle = state.showTitles,
                    isSelected = isSelected,
                    isHighlighted = highlightFile == item.file.name,
                    onModifierClick = { isAlt, isShift ->
                        if (isShift) {
                            viewModel.selectRange(item.file.absolutePath)
                        } else if (isAlt) {
                            viewModel.toggleSelection(item.file.absolutePath)
                        }
                    },
                    contextMenuContent = { dismiss ->
                        if (isDir) {
                            DropdownItem("Open") { dismiss(); onNavigateToFolder(item.file.absolutePath) }
                            DropdownItem(
                                "Pin to Library",
                                textColor = IrisTheme.colors.accentFolders
                            ) { dismiss(); viewModel.pinFolder(item.file.absolutePath) }
                        } else {
                            DropdownItem("Open") { dismiss(); onOpenMedia(item.file.absolutePath) }
                        }
                        DropdownItem("Rename") { dismiss(); onRenameItem(item.file.absolutePath) }
                        DropdownItem("Copy") {
                            dismiss()
                            val dest = openDirectoryPicker("Copy to...", path)
                            if (dest != null) viewModel.copyFiles(listOf(item.file.absolutePath), dest)
                        }
                        DropdownItem("Move") {
                            dismiss()
                            val dest = openDirectoryPicker("Move to...", path)
                            if (dest != null) viewModel.moveFiles(listOf(item.file.absolutePath), dest)
                        }
                        DropdownItem("Delete", textColor = IrisTheme.colors.danger) {
                            dismiss()
                            viewModel.deleteFiles(listOf(item.file.absolutePath))
                        }
                    }
                )
            }
        }

        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                .padding(end = 4.dp, top = 20.dp, bottom = 120.dp),
            adapter = rememberScrollbarAdapter(gridState)
        )
    }
}
