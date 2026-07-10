package io.iris.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vinceglb.filekit.compose.rememberDirectoryPickerLauncher
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
    onRemoveFolder: (String) -> Unit = {}
) {
    val viewModel = remember { HomeDependencies.viewModel }
    val state by viewModel.state.collectAsState()

    val launcher = rememberDirectoryPickerLauncher(
        title = "Select a folder to link"
    ) { directory ->
        directory?.path?.let { path ->
            viewModel.onFolderPicked(path)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(IrisTheme.colors.background)
    ) {
        if (state.linkedFolders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                IrisButton(
                    text = "+ Link Folder",
                    onClick = { launcher.launch() }
                )
            }
        } else {
            val gridState = rememberLazyGridState()
            val dragDropState = rememberGridDragDropState(gridState) { from, to ->
                viewModel.reorderLinkedFolders(from, to)
            }
            
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val count = state.linkedFolders.size
                    BasicText(
                        text = "$count folder${if (count == 1) "" else "s"}",
                        style = TextStyle(color = IrisTheme.colors.textSecondary, fontSize = 14.sp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(IrisTheme.colors.surface)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { /* Placeholder */ }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText("Select", style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 14.sp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(IrisTheme.colors.surface)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { launcher.launch() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText("+ Link Folder", style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 14.sp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    SortSwitcher(currentSort = state.sort, onSortChange = { viewModel.setSort(it) })
                    Spacer(modifier = Modifier.width(8.dp))
                    ViewLayoutSwitcher(currentLayout = state.layout, onLayoutChange = { viewModel.setLayout(it) })
                }
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
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
                        modifier = Modifier.fillMaxSize().dragContainer(dragDropState).padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.linkedFolders.size) { index ->
                            val path = state.linkedFolders[index]
                            val itemModifier = Modifier.draggableItem(index, dragDropState)
                            
                            FolderCard(
                                path = path,
                                modifier = itemModifier,
                                onClick = { onNavigateToFolder(path) },
                                onRemove = { onRemoveFolder(path) }
                            )
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(gridState)
                    )
                }
            }
        }
    }
}
