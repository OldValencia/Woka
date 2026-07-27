package com.woka.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import com.woka.data.MediaItem
import com.woka.ui.components.FolderContent
import com.woka.ui.components.LocalDockAccessories
import com.woka.ui.theme.Theme
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import com.woka.ui.viewer.NativeMediaViewer

class FolderDependencies(val path: String) : KoinComponent {
    val viewModel: FolderViewModel by inject { parametersOf(path) }
}

@Composable
fun FolderScreen(
    path: String,
    highlightFile: String? = null,
    openViewerForId: String? = null,
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
    var viewerMediaId by remember { mutableStateOf<String?>(openViewerForId) }
    var scrollToMediaId by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(openViewerForId) {
        if (openViewerForId != null) {
            viewerMediaId = openViewerForId
        }
    }

    val dockAccessories = LocalDockAccessories.current
    LaunchedEffect(viewerMediaId) {
        dockAccessories.isVisible = viewerMediaId == null
    }

    Column(modifier = Modifier.fillMaxSize().background(Theme.colors.background)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText(text = "Loading...", style = TextStyle(color = Theme.colors.textSecondary))
                }
            } else {
                FolderContent(
                    path = path,
                    highlightFile = highlightFile,
                    state = state,
                    viewModel = viewModel,
                    onNavigateToFolder = onNavigateToFolder,
                    onOpenMedia = { id -> viewerMediaId = id },
                    itemToRename = itemToRename,
                    showCreateFolderDialog = showCreateFolderDialog,
                    showBgContextMenu = showBgContextMenu,
                    bgContextMenuOffset = bgContextMenuOffset,
                    scrollToMediaId = scrollToMediaId,
                    onRenameItemChange = { itemToRename = it },
                    onShowCreateFolderDialogChange = { showCreateFolderDialog = it },
                    onShowBgContextMenuChange = { showBgContextMenu = it },
                    onBgContextMenuOffsetChange = { bgContextMenuOffset = it },
                    onScrollToMediaIdConsumed = { scrollToMediaId = null }
                )
            }
        }
    }
    
    if (viewerMediaId != null) {
        NativeMediaViewer(
            initialId = viewerMediaId!!,
            mediaItems = state.items.filter { it is MediaItem.Image || it is MediaItem.Video },
            onClose = { lastMediaId -> 
                viewerMediaId = null
                scrollToMediaId = lastMediaId
            }
        )
    }
}
