package com.woka.feature.viewer.shell

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.woka.app.AppDependencies
import com.woka.core.data.MediaItem
import com.woka.app.shell.LocalGlobalShortcuts
import com.woka.core.utils.copyFilesToClipboard
import com.woka.feature.viewer.pip.PipManager
import com.woka.feature.viewer.shell.frames.rememberFrameExportEntry
import com.woka.feature.viewer.shell.frames.rememberFrameStrip

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MediaViewerScreen(
    initialMediaId: String,
    mediaItems: List<MediaItem>,
    onClose: (String) -> Unit,
    thumbnails: ViewerThumbnails
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
        thumbnails = thumbnails
    )

    // Restored from settings and written back on change, so the chosen volume
    // survives switching files and app restarts.
    var mediaVolume by remember { mutableStateOf(settings.mediaVolume) }

    // The gate is "*this* item cleared the PiP conflict check", not the path being
    // non-null: activeItem changes a frame before activeItemPath catches up, so
    // keying on the raw path built — and probed — the player twice per file.
    val itemReady = state.activeItemPath != null &&
        state.activeItemPath == activeItem?.file?.absolutePath

    // Hoisted above the player because a Live Photo's motion frame plays a
    // *different file* from the one being viewed — see [rememberPlayedItem].
    val strip = rememberFrameStrip(activeItem)
    val playedItem = rememberPlayedItem(activeItem, state.frameIndex, strip)

    val videoState = rememberViewerVideoState(
        playedItem, itemReady, mediaVolume, state, settings, mediaItems
    )

    val focusRequester = remember { FocusRequester() }
    MediaViewerAmbientEffects(state, videoState, mediaVolume, settingsRepo, focusRequester)

    var showInfoDialog by remember { mutableStateOf(false) }
    val copyAction = {
        if (activeItem != null) copyFilesToClipboard(listOf(activeItem.file.absolutePath))
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
        onVolumeChange = { vol -> mediaVolume = vol; state.volumeEvent.raise() }
    )

    // Offered only when the file actually holds more than one picture — the
    // strip already knows, so nothing is opened twice to find out.
    val exportEntries = rememberFrameExportEntry(activeItem, strip)

    val media = rememberViewerMedia(state.imageBitmap, videoState, state.sourceSize, state.stillColor)

    val playerContent = @Composable {
        MediaViewerPlayer(
            modifier = playerModifier,
            state = state,
            media = media,
            mediaItems = mediaItems,
            activeItem = activeItem,
            mediaVolume = mediaVolume,
            chrome = ViewerChromeState(settings.mediaViewerCarouselMode, isFullscreen, settings.ambientFill),
            actions = ViewerActions(editAction, copyAction, infoAction, extra = exportEntries),
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
            thumbnails = thumbnails,
            strip = strip
        )
    }

    MediaViewerLayoutWrapper(
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
