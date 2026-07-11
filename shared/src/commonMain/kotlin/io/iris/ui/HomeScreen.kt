package io.iris.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.iris.data.ViewLayout
import io.iris.ui.components.*
import io.iris.ui.theme.IrisTheme
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object HomeDependencies : KoinComponent {
    val viewModel: HomeViewModel by inject()
}

@Composable
fun HomeScreen(
    onNavigateToFolder: (String) -> Unit = {},
    onRemoveFolder: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val viewModel = remember { HomeDependencies.viewModel }
    val state by viewModel.state.collectAsState()

    var showDirPicker by remember { mutableStateOf(false) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    if (showDirPicker) {
        showDirPicker = false
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            io.iris.utils.openDirectoryPicker("Select a folder to link")?.let { path ->
                viewModel.onFolderPicked(path)
            }
        }
    }

    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(IrisTheme.colors.background)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.linkedFolders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText(
                        text = "No linked folders",
                        style = TextStyle(color = IrisTheme.colors.textSecondary)
                    )
                }
            } else {
                val gridState = rememberLazyGridState()
                val isScrolled = gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0
                val dragDropState = rememberGridDragDropState(gridState) { from, to ->
                    viewModel.reorderLinkedFolders(from, to)
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type == PointerEventType.Scroll && event.keyboardModifiers.isCtrlPressed) {
                                        val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                        if (delta > 0) {
                                            val newLayout = when (state.layout) {
                                                ViewLayout.BigGrid -> ViewLayout.Grid
                                                ViewLayout.Grid -> ViewLayout.SmallGrid
                                                ViewLayout.SmallGrid -> ViewLayout.SmallGrid
                                            }
                                            viewModel.setLayout(newLayout)
                                        } else if (delta < 0) {
                                            val newLayout = when (state.layout) {
                                                ViewLayout.SmallGrid -> ViewLayout.Grid
                                                ViewLayout.Grid -> ViewLayout.BigGrid
                                                ViewLayout.BigGrid -> ViewLayout.BigGrid
                                            }
                                            viewModel.setLayout(newLayout)
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                ) {
                    val columns = when (state.layout) {
                        ViewLayout.SmallGrid -> GridCells.Adaptive(100.dp)
                        ViewLayout.BigGrid -> GridCells.Adaptive(240.dp)
                        ViewLayout.Grid -> GridCells.Adaptive(160.dp)
                    }

                    LazyVerticalGrid(
                        columns = columns,
                        state = gridState,
                        modifier = Modifier.fillMaxSize().dragContainer(dragDropState),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                        contentPadding = PaddingValues(top = 100.dp, bottom = 16.dp)
                    ) {
                        items(state.linkedFolders.size, key = { index -> state.linkedFolders[index] }) { index ->
                            val path = state.linkedFolders[index]
                            if (searchQuery.isBlank() || path.contains(searchQuery, ignoreCase = true)) {
                                val itemModifier = Modifier.draggableItem(index, dragDropState)
                                
                                val onClickAction = {
                                    if (state.isSelectMode) {
                                        viewModel.toggleSelection(path)
                                    } else {
                                        onNavigateToFolder(path)
                                    }
                                }

                                FolderCard(
                                    path = path,
                                    modifier = itemModifier.animateItem(),
                                    onClick = onClickAction,
                                    onRemove = if (state.isSelectMode) null else { { onRemoveFolder(path) } },
                                    showTitle = state.showTitles,
                                    isSelected = state.selectedPaths.contains(path),
                                    onModifierClick = { isShift, isAlt ->
                                        if (isShift) viewModel.selectRange(path)
                                        else if (isAlt) viewModel.toggleSelection(path)
                                    }
                                )
                            }
                        }
                    }
                }

                val count = state.linkedFolders.size
                LibraryHeader(
                    modifier = Modifier.align(Alignment.TopCenter).zIndex(1f),
                    isScrolled = isScrolled,
                    title = "Home",
                    subtitle = "$count items",
                    currentSort = state.sort,
                    onSortChange = { viewModel.setSort(it) },
                    currentLayout = state.layout,
                    onLayoutChange = { viewModel.setLayout(it) },
                    showTitles = state.showTitles,
                    onShowTitlesChange = { viewModel.setShowTitles(it) },
                    mediaFilter = "All",
                    onMediaFilterChange = {},
                    showMediaFilter = false,
                    onSelectClick = { viewModel.toggleSelectMode() },
                    isSelectMode = state.isSelectMode,
                    selectedCount = state.selectedPaths.size
                )

                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = 16.dp, end = 4.dp).zIndex(2f),
                    adapter = rememberScrollbarAdapter(gridState)
                )
            }
        }
            
        if (state.isSelectMode) {
            SelectionBottomMenu(
                onUnlinkClick = { 
                    state.selectedPaths.forEach { onRemoveFolder(it) }
                    viewModel.toggleSelectMode() // This turns off select mode and clears selection
                },
                selectedCount = state.selectedPaths.size
            )
        } else {
            BottomAppMenu(
                onSettingsClick = onOpenSettings,
                showLinkFolder = true,
                onLinkFolderClick = { showDirPicker = true },
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it }
            )
        }
    }
}
