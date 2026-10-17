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
import com.woka.app.shell.sidebar.samePath
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration.Companion.milliseconds

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
    // A pin keeps its own scroll; otherwise the place the last session left off, when
    // this is that folder — which is what a launch restores (SIDE-002).
    val currentSession = settings.pinnedSessions.find { it.path == path }
        ?: settings.lastSession?.takeIf { samePath(it.path, path) }

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

    LaunchedEffect(path) {
        // Written as the scroll settles, never at exit: closing is not a moment the
        // app can rely on, and `SettingsStore` flushes the last pending write on close.
        // The first emission writes the arrival itself. Cancelled when this folder
        // leaves composition, so an outgoing folder never overwrites the incoming one.
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collectLatest { (index, offset) ->
                delay(500.milliseconds)
                settingsRepo.setLastSession(PinnedSession(path, index, offset))
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
            // `linkedFolders` is what the sidebar's Folders lists; `pinnedSessions`
            // above is the Pinned section and does not answer this question.
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
            onEditMedia = onEditMedia,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }

    FolderOverlays(
        overlays = overlays,
        viewModel = viewModel,
        path = path,
        zoom = zoom,
        settingsRepo = settingsRepo
    )
}
