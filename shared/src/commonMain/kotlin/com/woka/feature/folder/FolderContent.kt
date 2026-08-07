package com.woka.feature.folder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.woka.app.AppDependencies
import com.woka.core.data.PinnedSession
import com.woka.core.data.ViewLayout

@Composable
fun FolderContent(
    path: String,
    state: FolderState,
    viewModel: FolderViewModel,
    overlays: FolderOverlayState,
    onNavigateToFolder: (String) -> Unit,
    onOpenMedia: (String) -> Unit,
    highlightFile: String? = null,
    scrollToMediaId: String? = null,
    onScrollToMediaIdConsumed: () -> Unit = {},
    offerPin: Boolean = false,
    onPinOfferResolved: () -> Unit = {}
) {
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
                settingsRepo.addPinnedSession(
                    PinnedSession(
                        path,
                        gridState.firstVisibleItemIndex,
                        gridState.firstVisibleItemScrollOffset
                    )
                )
            }
        }
    }

    val zoom = rememberFolderZoom(
        layout = settings.folderLayouts[path] ?: ViewLayout.Grid,
        path = path,
        settingsRepo = settingsRepo
    )

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(scrollToMediaId, state.items) {
        if (scrollToMediaId != null && state.items.isNotEmpty()) {
            val index = state.items.indexOfFirst { it.file.absolutePath == scrollToMediaId }
            if (index >= 0) {
                gridState.scrollToItem(index)
            }
            onScrollToMediaIdConsumed()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FolderDockSetup(
            path = path,
            state = state,
            isPinned = isPinned,
            zoom = zoom,
            gridScrollIndex = gridState.firstVisibleItemIndex,
            gridScrollOffset = gridState.firstVisibleItemScrollOffset,
            viewModel = viewModel,
            settingsRepo = settingsRepo,
            // `linkedFolders` is what Home lists; `pinnedSessions` above is the
            // Library's own concept and does not answer this question.
            showPinOffer = offerPin && path !in settings.linkedFolders,
            onPinOfferResolved = onPinOfferResolved
        )

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
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }

    FolderOverlays(
        overlays = overlays,
        viewModel = viewModel,
        path = path
    )
}
