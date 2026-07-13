package io.iris.ui

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
import io.iris.ui.components.FolderContent
import io.iris.ui.theme.IrisTheme
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf

class FolderDependencies(val path: String) : KoinComponent {
    val viewModel: FolderViewModel by inject { parametersOf(path) }
}

@Composable
fun FolderScreen(
    path: String,
    highlightFile: String? = null,
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
    var viewerMediaId by remember { mutableStateOf<String?>(null) }

    val dockAccessories = io.iris.ui.components.LocalDockAccessories.current
    LaunchedEffect(viewerMediaId) {
        dockAccessories.isVisible = viewerMediaId == null
    }

    Column(modifier = Modifier.fillMaxSize().background(IrisTheme.colors.background)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText(text = "Loading...", style = TextStyle(color = IrisTheme.colors.textSecondary))
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
                    onRenameItemChange = { itemToRename = it },
                    onShowCreateFolderDialogChange = { showCreateFolderDialog = it },
                    onShowBgContextMenuChange = { showBgContextMenu = it },
                    onBgContextMenuOffsetChange = { bgContextMenuOffset = it }
                )
            }
        }
    }
    
    if (viewerMediaId != null) {
        io.iris.ui.viewer.NativeMediaViewer(
            initialId = viewerMediaId!!,
            mediaItems = state.items.filter { it is io.iris.data.MediaItem.Image || it is io.iris.data.MediaItem.Video },
            onClose = { viewerMediaId = null }
        )
    }
}
