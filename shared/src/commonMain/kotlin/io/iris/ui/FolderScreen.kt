package io.iris.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import io.iris.AppDependencies
import io.iris.data.MediaItem
import io.iris.data.PinnedSession
import io.iris.data.SortType
import io.iris.data.ViewLayout
import io.iris.ui.components.DropdownItem
import io.iris.ui.components.FolderCard
import io.iris.ui.components.RenameDialog
import io.iris.ui.icons.IrisIcons
import io.iris.ui.theme.IrisTheme
import io.iris.utils.openDirectoryPicker
import org.jetbrains.compose.resources.painterResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf

class FolderDependencies(val path: String) : KoinComponent {
    val viewModel: FolderViewModel by inject { parametersOf(path) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FolderScreen(
    path: String,
    onNavigateToFolder: (String) -> Unit
) {
    val dependencies = remember(path) { FolderDependencies(path) }
    val viewModel = dependencies.viewModel
    val state by viewModel.state.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var itemToRename by remember { mutableStateOf<String?>(null) }
    var showBgContextMenu by remember { mutableStateOf(false) }
    var bgContextMenuOffset by remember { mutableStateOf(IntOffset.Zero) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

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
                                Image(
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
                        .onPointerEvent(PointerEventType.Press) { event ->
                            if (event.button == PointerButton.Secondary) {
                                val change = event.changes.first()
                                if (!change.isConsumed) {
                                    bgContextMenuOffset = IntOffset(change.position.x.toInt() + 2, change.position.y.toInt() + 2)
                                    showBgContextMenu = true
                                    change.consume()
                                }
                            }
                        }
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
                                    isSelected = isSelected,
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
                                            DropdownItem("Pin to Library", textColor = IrisTheme.colors.accentFolders) { dismiss(); viewModel.pinFolder(item.file.absolutePath) }
                                        } else {
                                            DropdownItem("Open") { dismiss(); /* Phase 3 Viewer */ }
                                        }
                                        DropdownItem("Rename") { dismiss(); itemToRename = item.file.absolutePath }
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
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp, top = 20.dp, bottom = 120.dp),
                            adapter = rememberScrollbarAdapter(gridState)
                        )
                    }
                }
            }
            
            itemToRename?.let { renamePath ->
                val file = java.io.File(renamePath)
                val ext = if (file.isDirectory || file.nameWithoutExtension.isEmpty()) "" else file.extension
                val nameWithoutExt = if (ext.isNotEmpty()) file.nameWithoutExtension else file.name
                RenameDialog(
                    initialName = nameWithoutExt,
                    onDismiss = { itemToRename = null },
                    onConfirm = { newName ->
                        val finalName = if (ext.isNotEmpty()) "$newName.$ext" else newName
                        viewModel.renameFile(renamePath, finalName)
                        itemToRename = null
                    }
                )
            }
            
            if (showCreateFolderDialog) {
                RenameDialog(
                    initialName = "New Folder",
                    title = "Create folder",
                    onDismiss = { showCreateFolderDialog = false },
                    onConfirm = { newName ->
                        viewModel.createFolder(newName)
                        showCreateFolderDialog = false
                    }
                )
            }
            
            if (showBgContextMenu) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = bgContextMenuOffset,
                    onDismissRequest = { showBgContextMenu = false }
                ) {
                    Column(
                        modifier = Modifier
                            .width(180.dp)
                            .shadow(12.dp, RoundedCornerShape(10.dp), spotColor = Color.Black.copy(alpha = 0.2f))
                            .background(IrisTheme.colors.surfaceElevated, RoundedCornerShape(10.dp))
                            .border(1.dp, IrisTheme.colors.border, RoundedCornerShape(10.dp))
                            .padding(6.dp)
                    ) {
                        DropdownItem("Create folder") {
                            showCreateFolderDialog = true
                            showBgContextMenu = false
                        }
                        DropdownItem("Pin to Library", textColor = IrisTheme.colors.accentFolders) {
                            viewModel.pinFolder(path)
                            showBgContextMenu = false
                        }
                    }
                }
            }
        }
    }
}
