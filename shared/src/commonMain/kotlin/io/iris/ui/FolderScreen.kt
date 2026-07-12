package io.iris.ui

import androidx.compose.ui.ExperimentalComposeUiApi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import io.iris.data.MediaItem
import io.iris.navigation.Screen
import io.iris.ui.components.FolderCard
import io.iris.ui.theme.IrisTheme
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.window.Popup
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.animation.animateColorAsState
import io.iris.AppDependencies
import io.iris.data.PinnedSession
import io.iris.data.ViewLayout
import io.iris.data.SortType
import io.iris.ui.icons.IrisIcons
import org.jetbrains.compose.resources.painterResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import io.iris.utils.openDirectoryPicker

class FolderDependencies(val path: String) : KoinComponent {
    val viewModel: FolderViewModel by inject { parametersOf(path) }
}

@OptIn(ExperimentalComposeUiApi::class)
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

    Column(modifier = Modifier.fillMaxSize().background(IrisTheme.colors.background)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText(text = "Loading...", style = TextStyle(color = IrisTheme.colors.textSecondary))
                }
            } else {
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
                
                var showFilterMenu by remember { mutableStateOf(false) }
                
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isPinned) IrisTheme.colors.surfaceHover else IrisTheme.colors.surfaceElevated)
                                .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(6.dp))
                                .clickable { 
                                    if (isPinned) {
                                        settingsRepo.removePinnedSession(path)
                                    } else {
                                        settingsRepo.addPinnedSession(PinnedSession(path, gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset))
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            BasicText(if (isPinned) "Pinned" else "Pin", style = TextStyle(color = if (isPinned) IrisTheme.colors.accentFolders else IrisTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))

                        if (state.isSelectMode) {
                            BasicText("${state.selectedPaths.size} selected", style = TextStyle(color = IrisTheme.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(IrisTheme.colors.surfaceElevated)
                                    .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(6.dp))
                                    .clickable { viewModel.selectAll() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                BasicText("Select All", style = TextStyle(color = IrisTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(IrisTheme.colors.surfaceElevated)
                                    .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(6.dp))
                                    .clickable { 
                                        if (state.selectedPaths.isNotEmpty()) {
                                            viewModel.duplicateFiles(state.selectedPaths.toList())
                                            viewModel.toggleSelectMode()
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                BasicText("Duplicate", style = TextStyle(color = if (state.selectedPaths.isNotEmpty()) IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(IrisTheme.colors.surfaceElevated)
                                    .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(6.dp))
                                    .clickable { 
                                        if (state.selectedPaths.isNotEmpty()) {
                                            val dest = openDirectoryPicker("Copy to...", path)
                                            if (dest != null) {
                                                viewModel.copyFiles(state.selectedPaths.toList(), dest)
                                                viewModel.toggleSelectMode()
                                            }
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                BasicText("Copy", style = TextStyle(color = if (state.selectedPaths.isNotEmpty()) IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(IrisTheme.colors.surfaceElevated)
                                    .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(6.dp))
                                    .clickable { 
                                        if (state.selectedPaths.isNotEmpty()) {
                                            val dest = openDirectoryPicker("Move to...", path)
                                            if (dest != null) {
                                                viewModel.moveFiles(state.selectedPaths.toList(), dest)
                                                viewModel.toggleSelectMode()
                                            }
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                BasicText("Move", style = TextStyle(color = if (state.selectedPaths.isNotEmpty()) IrisTheme.colors.textPrimary else IrisTheme.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(IrisTheme.colors.surfaceElevated)
                                    .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(6.dp))
                                    .clickable { 
                                        if (state.selectedPaths.isNotEmpty()) {
                                            viewModel.deleteFiles(state.selectedPaths.toList())
                                            viewModel.toggleSelectMode() // Exit after delete
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                BasicText("Delete", style = TextStyle(color = if (state.selectedPaths.isNotEmpty()) IrisTheme.colors.danger else IrisTheme.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (state.isSelectMode) IrisTheme.colors.surfaceHover else IrisTheme.colors.surfaceElevated)
                                .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(6.dp))
                                .clickable { viewModel.toggleSelectMode() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            BasicText(if (state.isSelectMode) "Done" else "Select", style = TextStyle(color = if (state.isSelectMode) IrisTheme.colors.accentFolders else IrisTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Box {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (showFilterMenu) IrisTheme.colors.surfaceHover else IrisTheme.colors.surfaceElevated)
                                    .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(6.dp))
                                    .clickable { showFilterMenu = !showFilterMenu }
                                    .padding(horizontal = 10.dp, vertical = 9.dp)
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = painterResource(IrisIcons.Static.Filter),
                                    contentDescription = "Filter",
                                    modifier = Modifier.width(21.dp).height(12.dp),
                                    colorFilter = ColorFilter.tint(IrisTheme.colors.textPrimary)
                                )
                            }
                            
                            if (showFilterMenu) {
                                Popup(
                                    alignment = Alignment.TopEnd,
                                    onDismissRequest = { showFilterMenu = false }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(top = 36.dp)
                                            .width(180.dp)
                                            .shadow(12.dp, RoundedCornerShape(10.dp), spotColor = Color.Black.copy(alpha = 0.2f))
                                            .background(IrisTheme.colors.surfaceElevated, RoundedCornerShape(10.dp))
                                            .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(10.dp))
                                            .padding(6.dp)
                                    ) {
                                        DropdownItem("Zoom In", enabled = layout != ViewLayout.BigGrid) { 
                                            zoomIn()
                                            showFilterMenu = false 
                                        }
                                        DropdownItem("Zoom Out", enabled = layout != ViewLayout.SmallGrid) { 
                                            zoomOut()
                                            showFilterMenu = false 
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(IrisTheme.colors.border))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val sort = state.sort
                                        val nameLabel = if (sort == SortType.NameAsc) "Name ↑" else if (sort == SortType.NameDesc) "Name ↓" else "Name"
                                        val dateLabel = if (sort == SortType.DateAsc) "Date ↑" else if (sort == SortType.DateDesc) "Date ↓" else "Date"
                                        
                                        DropdownItem("Sort by $nameLabel") { 
                                            viewModel.setSort(if (sort == SortType.NameAsc) SortType.NameDesc else SortType.NameAsc)
                                            showFilterMenu = false 
                                        }
                                        DropdownItem("Sort by $dateLabel") { 
                                            viewModel.setSort(if (sort == SortType.DateAsc) SortType.DateDesc else SortType.DateAsc)
                                            showFilterMenu = false 
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().weight(1f)
                        .focusRequester(focusRequester)
                        .focusable()
                        .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { event ->
                            if (event.keyboardModifiers.isCtrlPressed) {
                                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                if (delta < 0) {
                                    zoomIn()
                                } else if (delta > 0) {
                                    zoomOut()
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.isCtrlPressed) {
                                when (event.key) {
                                    Key.Equals, Key.Plus, Key.NumPadAdd -> {
                                        zoomIn()
                                        true
                                    }
                                    Key.Minus, Key.NumPadSubtract -> {
                                        zoomOut()
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
                            // Paddings from mockup: 20px 40px 120px 40px
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
                                        else { /* Phase 3: Open Viewer */ }
                                    }
                                }
                                FolderCard(
                                    path = item.file.absolutePath,
                                    onClick = onClickAction,
                                    showTitle = state.showTitles,
                                    isSelected = isSelected
                                )
                            }
                        }
                        
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp, top = 20.dp, bottom = 120.dp),
                            adapter = rememberScrollbarAdapter(gridState)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DropdownItem(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val isHovered by src.collectIsHoveredAsState()
    val bg by animateColorAsState(if (isHovered && enabled) IrisTheme.colors.surfaceHover else Color.Transparent)
    val fg = if (enabled) IrisTheme.colors.textPrimary else IrisTheme.colors.textTertiary
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .then(if (enabled) Modifier.clickable(interactionSource = src, indication = null, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        BasicText(text, style = TextStyle(color = fg, fontSize = 13.sp))
    }
}
