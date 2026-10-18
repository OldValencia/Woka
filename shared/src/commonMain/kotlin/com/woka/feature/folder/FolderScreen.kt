package com.woka.feature.folder

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
import com.woka.core.data.MediaItem
import com.woka.core.data.isReadOnlyMedia
import com.woka.core.ui.theme.Theme
import com.woka.feature.editor.EditorNoticeStatus
import com.woka.feature.editor.EditorTarget
import com.woka.feature.editor.NativeMediaEditor
import com.woka.feature.viewer.NativeMediaViewer
import com.woka.core.utils.directoryChanges
import java.io.File
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import org.koin.core.component.KoinComponent
import kotlin.time.Duration.Companion.milliseconds
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf

class FolderDependencies(val path: String) : KoinComponent {
    val viewModel: FolderViewModel by inject { parametersOf(path) }
}

@OptIn(FlowPreview::class)
@Composable
fun FolderScreen(
    path: String,
    highlightFile: String? = null,
    openViewerForId: String? = null,
    offerPin: Boolean = false,
    onNavigateToFolder: (String) -> Unit
) {
    val dependencies = remember(path) { FolderDependencies(path) }
    val viewModel = dependencies.viewModel
    val state by viewModel.state.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val overlays = remember { FolderOverlayState() }
    var viewerMediaId by remember { mutableStateOf<String?>(openViewerForId) }
    // The editor is an overlay beside the viewer, not on top of the navigation stack:
    // the viewer is one too, and this is what lets Cancel return where the user
    // actually was — the grid from the grid, the frozen viewer from the viewer (§9).
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }
    var scrollToMediaId by remember { mutableStateOf<String?>(null) }
    // Taken or dismissed, the offer does not come back for this screen.
    var pinOfferOpen by remember(path) { mutableStateOf(offerPin) }

    LaunchedEffect(openViewerForId) {
        if (openViewerForId != null) {
            viewerMediaId = openViewerForId
        }
    }

    // The folder follows the disk while it is on screen (SIDE-007). Here, not in the
    // ViewModel, whose scope outlives the screen: started there, every folder ever
    // opened would stay watched. Debounced, because one file written is a creation
    // and a run of modifications, and one quiet re-read answers all of them.
    LaunchedEffect(path) {
        directoryChanges(path)
            .debounce(300.milliseconds)
            .collect { viewModel.refresh(quiet = true) }
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
                    state = state,
                    viewModel = viewModel,
                    overlays = overlays,
                    onNavigateToFolder = onNavigateToFolder,
                    onOpenMedia = { id -> viewerMediaId = id },
                    onEditMedia = { id ->
                        if (File(id).isReadOnlyMedia) {
                            EditorNoticeStatus.show()
                        } else {
                            editorTarget = EditorTarget(id)
                        }
                    },
                    highlightFile = highlightFile,
                    scrollToMediaId = scrollToMediaId,
                    onScrollToMediaIdConsumed = { scrollToMediaId = null },
                    offerPin = pinOfferOpen,
                    onPinOfferResolved = { pinOfferOpen = false }
                )
            }
        }
    }

    // Remembered, not filtered inline: a fresh list every recomposition changes
    // identity and invalidates everything the viewer keys on it (§10.8).
    val viewerItems = remember(state.items) {
        state.items.filter { it is MediaItem.Image || it is MediaItem.Video }
    }

    if (viewerMediaId != null) {
        NativeMediaViewer(
            initialId = viewerMediaId!!,
            mediaItems = viewerItems,
            onClose = { lastMediaId ->
                viewerMediaId = null
                scrollToMediaId = lastMediaId
            },
            onEdit = { id, frame -> editorTarget = EditorTarget(id, frame) },
            // The editor mounts over it and leaves it composed; without this its
            // animation would keep decoding under an opaque panel.
            isFrozen = editorTarget != null
        )
    }

    // Last, so it draws over the viewer — which stays mounted and frozen underneath,
    // keeping its exact zoom, pan and frame for when Back returns to it (§4.1).
    // Not wrapped in a `let`: the editor takes a nullable target and animates itself
    // out, which it cannot do if the call site removes it from the tree first.
    run {
        NativeMediaEditor(
            target = editorTarget,
            onClose = { editorTarget = null },
            onSaved = { savedPath ->
                // A copy is a path the grid has never seen and nothing watches the
                // directory for it, so the listing is re-read against it rather than
                // hopefully refreshed.
                viewModel.refresh()
                scrollToMediaId = savedPath
            }
        )
    }
}
