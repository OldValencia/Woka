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
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

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

    LaunchedEffect(activeItem) {
        if (PipManager.isPipActive && activeItem != null && activeItem.file.absolutePath != PipManager.activeItem?.file?.absolutePath) {
            showConflictDialog = true
        } else {
            if (activeItem != null) {
                state.imageBitmap = loadThumbnail(activeItem)
                state.activeItemPath = activeItem.file.absolutePath
                // Returning from fullscreen (PiP) is the same file: carry the size
                // state back instead of resetting it to the global preset.
                val fromPip = PipManager.isTransitioningToMain &&
                    PipManager.activeItem?.file?.absolutePath == activeItem.file.absolutePath
                if (fromPip) {
                    state.sizeMode = PipManager.sizeMode
                    state.offsetX = PipManager.offsetX; state.offsetY = PipManager.offsetY
                } else {
                    state.sizeMode = startSizeMode(settings)
                    state.offsetX = 0f; state.offsetY = 0f
                }
            } else {
                state.activeItemPath = null
            }
        }
    }

    if (showConflictDialog) {
        VideoAlreadyOpenedDialog(
            onConfirm = {
                PipManager.closePip()
                showConflictDialog = false
                if (activeItem != null) {
                    state.activeItemPath = activeItem.file.absolutePath
                    state.sizeMode = startSizeMode(settings); state.offsetX = 0f; state.offsetY = 0f
                }
            },
            onDismiss = {
                showConflictDialog = false
                onClose(state.currentId)
            }
        )
    }

    // Load thumbnail lazily if confirmed, for now we skip loading
    LaunchedEffect(activeItem, showConflictDialog) {
        if (!showConflictDialog && activeItem != null) {
            state.imageBitmap = loadThumbnail(activeItem)
        }
    }

    var mediaVolume by remember { mutableStateOf(0.5f) }

    val videoState = remember(activeItem, state.activeItemPath) {
        if (activeItem is MediaItem.Video && state.activeItemPath != null) {
            PipManager.createOrGetState(activeItem, mediaVolume)
        } else null
    }

    DisposableEffect(videoState) {
        onDispose {
            PipManager.releaseState(videoState)
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(state.isVolumeHoveredRaw) {
        if (state.isVolumeHoveredRaw) state.isVolumeHovered = true
        else {
            delay(300.milliseconds); state.isVolumeHovered = false
        }
    }
    LaunchedEffect(state.volumeTrigger) {
        if (state.volumeTrigger > 0) {
            state.volumeTriggerTimer = 1; delay(2000.milliseconds); state.volumeTriggerTimer = 0
        }
    }
    LaunchedEffect(mediaVolume) { videoState?.updateVolume(mediaVolume) }
    LaunchedEffect(state.lastInteractionTime, videoState?.isPlaying, state.isVolumeHoveredRaw) {
        // Keep the UI up while the cursor is on the volume/audio popover; otherwise
        // the auto-hide fires under a stationary cursor, the popover jumps position
        // and blinks.
        if (videoState?.isPlaying == false || state.isVolumeHoveredRaw) {
            state.isUiVisible = true
        } else {
            delay(2500.milliseconds)
            state.isUiVisible = false
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    var showInfoDialog by remember { mutableStateOf(false) }
    var copyTrigger by remember { mutableStateOf(0L) }
    val copyAction = {
        if (activeItem != null) {
            copyFileToClipboard(activeItem.file)
            copyTrigger = System.currentTimeMillis()
        }
    }
    val infoAction = {
        if (activeItem != null) showInfoDialog = true
    }
    val editAction = {
        onClose(state.currentId)
    }
    val onSizeModeChange: (SizeMode) -> Unit = { mode ->
        state.sizeMode = mode
        if (settings.rememberSizeState) persistSizeState(settingsRepo, mode)
    }

    // Register the viewer shortcuts app-wide so they fire in the key-event
    // preview phase — before any focused button gets the key. This makes Space,
    // arrows, Esc, etc. work no matter what currently holds focus (e.g. after
    // clicking a toolbar/window button) and stops those keys from also
    // activating the focused button. Fresh values are read via rememberUpdatedState.
    val globalShortcuts = LocalGlobalShortcuts.current
    val curVideoState by rememberUpdatedState(videoState)
    val curMediaItems by rememberUpdatedState(mediaItems)
    val curVolume by rememberUpdatedState(mediaVolume)
    val curCopy by rememberUpdatedState(copyAction)
    val curInfo by rememberUpdatedState(infoAction)
    DisposableEffect(globalShortcuts) {
        globalShortcuts.onPreviewKey = { event ->
            handleMediaViewerKeyEvent(
                event = event,
                currentId = state.currentId,
                mediaItems = curMediaItems,
                videoState = curVideoState,
                mediaVolume = curVolume,
                onClose = { onClose(state.currentId) },
                onSkip = { dir -> state.skipDirection = dir; state.skipTrigger = System.currentTimeMillis() },
                onNavigate = { dir, newId -> state.animationDirection = dir; state.currentId = newId },
                onVolumeChange = { vol -> mediaVolume = vol; state.volumeTrigger = System.currentTimeMillis() },
                onPlayPause = { state.playPauseTrigger = System.currentTimeMillis() },
                onCopy = { curCopy() },
                onInfo = { curInfo() }
            )
        }
        onDispose { globalShortcuts.onPreviewKey = null }
    }

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
        savedPreset = settings.scalePreset,
        popoverView = settings.zoomPopoverView,
        onSizeModeChange = onSizeModeChange,
        onOffsetReset = { state.offsetX = 0f; state.offsetY = 0f },
        onPopoverViewChange = { settingsRepo.setZoomPopoverView(it) },
        carouselMode = settings.mediaViewerCarouselMode,
        settingsRepo = settingsRepo,
        currentId = state.currentId,
        onClose = onClose,
        onEdit = editAction,
        onCopy = copyAction,
        copyTrigger = copyTrigger,
        onInfo = infoAction,
        isInfoOpen = showInfoDialog,
        playerContent = playerContent
    )

}
