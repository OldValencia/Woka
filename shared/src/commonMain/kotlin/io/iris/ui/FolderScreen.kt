package io.iris.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.iris.data.MediaItem
import io.iris.data.ViewLayout
import io.iris.navigation.Screen
import io.iris.ui.components.BottomAppMenu
import io.iris.ui.components.FolderCard
import io.iris.ui.components.GridLine
import io.iris.ui.components.LibraryHeader
import io.iris.ui.components.rememberKnownLayoutScrollbarAdapter
import io.iris.ui.icons.IrisIcons
import io.iris.ui.theme.IrisTheme
import org.jetbrains.compose.resources.painterResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import java.io.File

class FolderDependencies(val path: String) : KoinComponent {
    val viewModel: FolderViewModel by inject { parametersOf(path) }
}

@Composable
fun FolderScreen(
    path: String,
    stack: List<Screen>,
    onPopTo: (Screen) -> Unit,
    onNavigateToFolder: (String) -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val dependencies = remember(path) { FolderDependencies(path) }
    val viewModel = dependencies.viewModel
    val state by viewModel.state.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(IrisTheme.colors.background)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText(text = "Loading...", style = TextStyle(color = IrisTheme.colors.textSecondary))
                }
            } else {
                val gridState = rememberLazyGridState()
                val isScrolled = gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0

                val itemsToShow = if (searchQuery.isBlank()) {
                    state.items
                } else {
                    state.items.filter { it.file.name.contains(searchQuery, ignoreCase = true) }
                }
                val folders = itemsToShow.filterIsInstance<MediaItem.Folder>()
                val files = itemsToShow.filter { it !is MediaItem.Folder }

                // The grid mixes short "Folders"/"Media" header rows with much taller square
                // FolderCard rows. Compose's built-in scrollbar adapter estimates content size by
                // averaging the height of whatever rows are currently visible, which this mix
                // throws off enough that a fast scrollbar drag can get stuck short of the real
                // end (see https://github.com/JetBrains/compose-multiplatform/issues/2679). Since
                // we already know exactly how many rows of each kind exist, compute it exactly.
                val scrollbarAdapter = rememberKnownLayoutScrollbarAdapter(gridState) {
                    buildList {
                        if (folders.isNotEmpty()) {
                            add(GridLine.Header("folders_header"))
                            if (state.showFolders) add(GridLine.Cells(folders.size))
                        }
                        if (files.isNotEmpty()) {
                            add(GridLine.Header("media_header"))
                            add(GridLine.Cells(files.size))
                        }
                    }
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
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                        contentPadding = PaddingValues(top = 90.dp, bottom = 16.dp)
                    ) {
                        if (folders.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon.Hand)
                                        .clickable { viewModel.setShowFolders(!state.showFolders) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicText("Folders", style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Image(
                                        painter = painterResource(IrisIcons.Static.ChevronRight),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp).rotate(if (state.showFolders) 90f else 0f),
                                        colorFilter = ColorFilter.tint(IrisTheme.colors.textSecondary)
                                    )
                                }
                            }

                            if (state.showFolders) {
                                items(folders, key = { it.file.absolutePath }) { item ->
                                    val onClickAction = { onNavigateToFolder(item.file.absolutePath) }
                                    FolderCard(
                                        path = item.file.absolutePath,
                                        onClick = onClickAction,
                                        showTitle = state.showTitles,
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }

                        if (files.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicText("Media", style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                                }
                            }

                            items(files, key = { it.file.absolutePath }) { item ->
                                val onClickAction = { /* Phase 3: Open Viewer */ }
                                FolderCard(
                                    path = item.file.absolutePath,
                                    onClick = onClickAction,
                                    showTitle = state.showTitles,
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .zIndex(1f)
                        .background(if (isScrolled) IrisTheme.colors.surface.copy(alpha = 0.85f) else Color.Transparent)
                ) {
                    val currentDirName = File(path).name
                    val filesCount = state.items.count { it !is MediaItem.Folder }
                    val countText = "$filesCount item${if (filesCount != 1) "s" else ""}"

                    val breadcrumbsList = stack.mapNotNull { screen ->
                        if (screen is Screen.Home) "Home" to { onPopTo(screen) }
                        else if (screen is Screen.Folder) File(screen.path).name to { onPopTo(screen) }
                        else null
                    }
                    val parentScreen = stack.getOrNull(stack.size - 2) ?: stack.lastOrNull()

                    LibraryHeader(
                        title = currentDirName,
                        subtitle = countText,
                        currentSort = state.sort,
                        onSortChange = { viewModel.setSort(it) },
                        currentLayout = state.layout,
                        onLayoutChange = { viewModel.setLayout(it) },
                        showTitles = state.showTitles,
                        onShowTitlesChange = { viewModel.setShowTitles(it) },
                        mediaFilter = state.mediaFilter,
                        onMediaFilterChange = { viewModel.setMediaFilter(it) },
                        showMediaFilter = true,
                        onSelectClick = { /* Select action */ },
                        onBackClick = { parentScreen?.let { onPopTo(it) } },
                        breadcrumbs = breadcrumbsList
                    )
                }

                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(top = 90.dp, end = 4.dp, bottom = 13.dp)
                        .zIndex(2f),
                    adapter = scrollbarAdapter
                )
            }
        }

        BottomAppMenu(
                onSettingsClick = onOpenSettings,
                showLinkFolder = false,
                onLinkFolderClick = {},
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it }
        )
    }
}
