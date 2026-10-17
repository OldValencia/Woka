package com.woka.feature.folder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.woka.app.AppDependencies
import androidx.compose.ui.input.key.KeyEvent
import com.woka.core.data.MediaItem
import com.woka.core.ui.components.rememberWatchMarks
import java.io.File

/**
 * A folder's grid and what sits on it: the toolbar's folder controls, the link offer
 * at the top and the selection bar at the bottom (SIDE-003). The scroll it opens at,
 * and the record of where it was left, are `rememberFolderGridState`'s.
 */
@Composable
fun FolderContent(
    path: String,
    state: FolderState,
    viewModel: FolderViewModel,
    overlays: FolderOverlayState,
    onNavigateToFolder: (String) -> Unit,
    onOpenMedia: (String) -> Unit,
    onEditMedia: (String) -> Unit,
    highlightFile: String? = null,
    scrollToMediaId: String? = null,
    onScrollToMediaIdConsumed: () -> Unit = {},
    offerPin: Boolean = false,
    onPinOfferResolved: () -> Unit = {}
) {
    val settingsRepo = remember { AppDependencies.settingsRepository }
    val settings by settingsRepo.settings.collectAsState()
    val isPinned = settings.pinnedSessions.any { it.path == path }
    val gridState = rememberFolderGridState(path)

    val zoom = rememberFolderZoom(
        layout = settings.gridLayout,
        path = path,
        settingsRepo = settingsRepo
    )

    FolderToolbarSetup(path, state, isPinned, onTogglePin = {
        if (isPinned) settingsRepo.removePinnedSession(path)
        else settingsRepo.addPinnedSession(gridState.placeIn(path))
    })

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    val focus = remember(path) { FolderFocus() }
    val scope = rememberCoroutineScope()
    val open = { item: MediaItem ->
        val target = item.file.absolutePath
        if (item is MediaItem.Folder) onNavigateToFolder(target) else onOpenMedia(target)
    }
    val onKey = { event: KeyEvent ->
        val roots = settings.linkedFolders
        val context = FolderKeyContext(
            state, focus, gridState, scope, viewModel, roots, path, open, onNavigateToFolder
        )
        folderKey(event, context)
    }

    LaunchedEffect(scrollToMediaId, state.items) {
        if (scrollToMediaId != null && state.items.isNotEmpty()) {
            val index = state.items.indexOfFirst { it.file.absolutePath == scrollToMediaId }
            if (index >= 0) {
                gridState.scrollToItem(state.items.gridIndexOf(index))
            }
            // The viewer has just closed: its last file is the keyboard's item, and the
            // grid takes focus back so the arrows work without a click first (SIDE-005).
            focus.focused = scrollToMediaId
            focusRequester.requestFocus()
            onScrollToMediaIdConsumed()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FolderGrid(
            state = state,
            viewModel = viewModel,
            gridState = gridState,
            zoom = zoom,
            path = path,
            highlightFile = highlightFile,
            focusRequester = focusRequester,
            onNavigateToFolder = onNavigateToFolder,
            overlays = overlays,
            onOpenMedia = onOpenMedia,
            onEditMedia = onEditMedia,
            focus = focus,
            onKey = onKey,
            modifier = Modifier.fillMaxSize()
        )

        // `linkedFolders` is what the sidebar's Folders lists; `pinnedSessions` is
        // the Pinned section and does not answer this question.
        if (offerPin && path !in settings.linkedFolders) {
            FolderPinPrompt(
                folderName = File(path).name,
                onPin = {
                    settingsRepo.addLinkedFolder(path)
                    onPinOfferResolved()
                },
                onDismiss = onPinOfferResolved,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
            )
        }

        if (state.isSelectMode) {
            FolderSelectionBar(
                path = path,
                state = state,
                viewModel = viewModel,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
            )
        }

        val looked = state.items.firstOrNull { it.file.absolutePath == focus.focused }
        if (focus.quickLook && looked != null && looked !is MediaItem.Folder) {
            val marks = rememberWatchMarks(viewModel.settingsRepository)
            QuickLook(looked, marks.forVideo(looked), onClose = { focus.quickLook = false })
        }
    }

    FolderOverlays(
        overlays = overlays,
        viewModel = viewModel,
        path = path,
        zoom = zoom,
        settingsRepo = settingsRepo
    )
}
