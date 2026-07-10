package io.iris.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.iris.data.MediaItem
import io.iris.ui.theme.IrisTheme
import io.iris.ui.components.SortSwitcher
import io.iris.ui.components.ViewLayoutSwitcher
import io.iris.data.ViewLayout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import java.io.File

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import io.iris.navigation.Screen

class FolderDependencies(val path: String) : KoinComponent {
    val viewModel: FolderViewModel by inject { parametersOf(path) }
}

@Composable
fun FolderScreen(
    path: String,
    stack: List<Screen>,
    onPopTo: (Screen) -> Unit,
    onNavigateToFolder: (String) -> Unit
) {
    val dependencies = remember(path) { FolderDependencies(path) }
    val viewModel = dependencies.viewModel
    val state by viewModel.state.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(IrisTheme.colors.background)) {
        val breadcrumbScrollState = rememberScrollState()
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(breadcrumbScrollState).padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val breadcrumbs = stack.mapNotNull { screen ->
                            if (screen is Screen.Home) "Home" to screen
                            else if (screen is Screen.Folder) File(screen.path).name to screen
                            else null
                        }
                        
                        breadcrumbs.forEachIndexed { index, (name, screen) ->
                            BasicText(
                                text = name,
                                modifier = Modifier.clickable { onPopTo(screen) },
                                style = TextStyle(
                                    color = if (index == breadcrumbs.lastIndex) IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary, 
                                    fontSize = 16.sp,
                                    fontWeight = if (index == breadcrumbs.lastIndex) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                            if (index < breadcrumbs.lastIndex) {
                                BasicText(
                                    text = " > ",
                                    style = TextStyle(color = IrisTheme.colors.textSecondary, fontSize = 16.sp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    HorizontalScrollbar(
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                        adapter = rememberScrollbarAdapter(breadcrumbScrollState)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                
                val foldersCount = state.items.count { it is MediaItem.Folder }
                val filesCount = state.items.count { it !is MediaItem.Folder }
                val countText = buildString {
                    if (foldersCount > 0) append("$foldersCount folder${if (foldersCount > 1) "s" else ""}")
                    if (foldersCount > 0 && filesCount > 0) append(", ")
                    if (filesCount > 0) append("$filesCount file${if (filesCount > 1) "s" else ""}")
                    if (foldersCount == 0 && filesCount == 0) append("0 items")
                }
                BasicText(
                    text = countText,
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
                
                SortSwitcher(
                    currentSort = state.sort,
                    onSortChange = { viewModel.setSort(it) },
                    allowCustomOrder = false
                )
                Spacer(modifier = Modifier.width(8.dp))
                ViewLayoutSwitcher(currentLayout = state.layout, onLayoutChange = { viewModel.setLayout(it) })
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BasicText(text = "Loading...", style = TextStyle(color = IrisTheme.colors.textSecondary))
            }
        } else {
            val gridState = rememberLazyGridState()
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
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.items) { item ->
                        val onClickAction = {
                            if (item is MediaItem.Folder) onNavigateToFolder(item.file.absolutePath)
                            else { /* Phase 3: Open Viewer */ }
                        }
                        io.iris.ui.components.FolderCard(
                            path = item.file.absolutePath,
                            onClick = onClickAction
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
