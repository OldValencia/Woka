package com.woka.ui.viewer

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ImageBitmap
import com.woka.AppDependencies
import com.woka.data.MediaItem
import com.woka.ui.components.LocalGlobalShortcuts
import com.woka.ui.viewer.pip.PipManager

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MediaViewerScreen(
    initialMediaId: String,
    mediaItems: List<MediaItem>,
    onClose: (String) -> Unit,
    loadThumbnail: suspend (MediaItem) -> ImageBitmap?
) {
    val settingsRepo = remember { AppDependencies.settingsRepository }
    val settings by settingsRepo.settings.collectAsState()
    val state = rememberMediaViewerState(initialMediaId)
    val activeItem = remember(state.currentId, mediaItems) {
        mediaItems.find { it.file.absolutePath == state.currentId }
    }
    var isFullscreen by remember { mutableStateOf(false) }
    var showConflictDialog by remember { mutableStateOf(false) }

    MediaViewerActiveItemEffects(
        state = state,
        activeItem = activeItem,
        settings = settings,
        showConflictDialog = showConflictDialog,
        onConflictChange = { showConflictDialog = it },
        onClose = onClose,
        loadThumbnail = loadThumbnail
    )

    // Restored from settings and written back on change, so the chosen volume
    // survives switching files and app restarts.
    var mediaVolume by remember { mutableStateOf(settings.mediaVolume) }

    val videoState = remember(activeItem, state.activeItemPath) {
        if (activeItem is MediaItem.Video && state.activeItemPath != null) {
            PipManager.createOrGetState(activeItem, mediaVolume)
        } else null
    }

    DisposableEffect(videoState) {
        onDispose { PipManager.releaseState(videoState) }
    }

    MediaViewerPlaybackEffects(state, videoState, settings, mediaItems)

    val focusRequester = remember { FocusRequester() }
    MediaViewerAmbientEffects(state, videoState, mediaVolume, settingsRepo, focusRequester)

    var showInfoDialog by remember { mutableStateOf(false) }
    val copyAction = {
        if (activeItem != null) copyFileToClipboard(activeItem.file)
    }
    val infoAction = { if (activeItem != null) showInfoDialog = true }
    val editAction = { onClose(state.currentId) }
    val onSizeModeChange: (SizeMode) -> Unit = { mode ->
        state.sizeMode = mode
        if (settings.rememberSizeState) persistSizeState(settingsRepo, mode)
    }

    MediaViewerShortcutEffect(
        state = state,
        mediaItems = mediaItems,
        videoState = videoState,
        mediaVolume = mediaVolume,
        globalShortcuts = LocalGlobalShortcuts.current,
        onClose = onClose,
        onVolumeChange = { mediaVolume = it },
        onCopy = { copyAction() },
        onInfo = { infoAction() },
        onSizeModeChange = onSizeModeChange
    )

    val playerModifier = Modifier.mediaViewerPlayerModifier(
        state = state,
        videoState = videoState,
        mediaItems = mediaItems,
        mediaVolume = mediaVolume,
        isFullscreen = isFullscreen,
        focusRequester = focusRequester,
        onClose = onClose,
        copyAction = copyAction,
        infoAction = infoAction,
        onSizeModeChange = onSizeModeChange,
        onVolumeChange = { vol -> mediaVolume = vol; state.volumeTrigger = System.currentTimeMillis() }
    )

    val playerContent = @Composable {
        MediaViewerPlayer(
            modifier = playerModifier,
            state = state,
            videoState = videoState,
            mediaItems = mediaItems,
            activeItem = activeItem,
            mediaVolume = mediaVolume,
            carouselMode = settings.mediaViewerCarouselMode,
            isFullscreen = isFullscreen,
            showInfoDialog = showInfoDialog,
            onShowInfoDialogChange = { showInfoDialog = it },
            onVolumeChange = { mediaVolume = it },
            onSizeModeChange = onSizeModeChange,
            onToggleFullscreen = {
                if (videoState != null && activeItem != null) {
                    PipManager.openPip(activeItem, videoState, state.sizeMode, state.offsetX, state.offsetY)
                    onClose(state.currentId)
                }
            },
            loadThumbnail = loadThumbnail,
            editAction = editAction,
            copyAction = copyAction,
            infoAction = infoAction
        )
    }

    MediaViewerLayoutWrapper(
        isVisible = state.isUiVisible,
        sizeMode = state.sizeMode,
        containerSize = state.containerSize,
        nativeSize = state.nativeSize,
        onSizeModeChange = onSizeModeChange,
        onOffsetReset = { state.offsetX = 0f; state.offsetY = 0f },
        carouselMode = settings.mediaViewerCarouselMode,
        settingsRepo = settingsRepo,
        currentId = state.currentId,
        onClose = onClose,
        onEdit = editAction,
        onInfo = infoAction,
        isInfoOpen = showInfoDialog,
        playerContent = playerContent
    )
}
