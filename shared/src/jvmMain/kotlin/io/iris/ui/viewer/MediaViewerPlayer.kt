package io.iris.ui.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import io.iris.data.CarouselVisibility
import io.iris.data.MediaItem
import io.iris.ui.viewer.player.VideoPlayerState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import io.iris.ui.theme.IrisTheme

@Composable
fun MediaViewerPlayer(
    modifier: Modifier,
    state: MediaViewerState,
    videoState: VideoPlayerState?,
    mediaItems: List<MediaItem>,
    activeItem: MediaItem?,
    mediaVolume: Float,
    carouselMode: CarouselVisibility,
    isFullscreen: Boolean,
    showInfoDialog: Boolean,
    onShowInfoDialogChange: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleFullscreen: () -> Unit,
    loadThumbnail: suspend (MediaItem) -> ImageBitmap?,
    editAction: () -> Unit,
    copyAction: () -> Unit,
    infoAction: () -> Unit
) {
    Box(modifier = modifier) {
        if (state.imageBitmap != null) {
            MediaViewerContent(
                imageBitmap = state.imageBitmap!!, animationDirection = state.animationDirection,
                scale = state.scale, offsetX = state.offsetX, offsetY = state.offsetY, videoState = videoState,
                onScaleChange = { state.scale = it }, onOffsetChange = { x, y -> state.offsetX = x; state.offsetY = y },
                onPlayPause = { state.playPauseTrigger = System.currentTimeMillis() },
                onEdit = editAction,
                onCopy = copyAction,
                onInfo = infoAction,
                onToggleFullscreen = onToggleFullscreen
            )
            MediaViewerToolbar(
                currentId = state.currentId, mediaItems = mediaItems, imageBitmap = state.imageBitmap!!,
                isVisible = state.isUiVisible || carouselMode == CarouselVisibility.ALWAYS_ON,
                isCarouselEnabled = carouselMode != CarouselVisibility.HIDDEN,
                scale = state.scale, offsetX = state.offsetX, offsetY = state.offsetY,
                onPrev = {
                    val idx = mediaItems.indexOfFirst { it.file.absolutePath == state.currentId }
                    if (idx > 0) {
                        state.animationDirection = -1; state.currentId = mediaItems[idx - 1].file.absolutePath
                    }
                },
                onNext = {
                    val idx = mediaItems.indexOfFirst { it.file.absolutePath == state.currentId }
                    if (idx in 0 until mediaItems.lastIndex) {
                        state.animationDirection = 1; state.currentId = mediaItems[idx + 1].file.absolutePath
                    }
                },
                onSelectMedia = { state.animationDirection = 0; state.currentId = it },
                onVolumeHoverChange = { state.isVolumeHoveredRaw = it },
                loadThumbnail = loadThumbnail,
                mediaVolume = mediaVolume,
                onVolumeChange = onVolumeChange,
                videoState = videoState,
                isFullscreen = isFullscreen,
                onToggleFullscreen = onToggleFullscreen
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        PlayPauseOverlay(trigger = state.playPauseTrigger, isPlaying = videoState?.isPlaying == true)
        SkipOverlay(trigger = state.skipTrigger, direction = state.skipDirection)
        VolumeVerticalOverlay(
            isVisible = state.isVolumeHovered || state.volumeTriggerTimer > 0,
            volume = mediaVolume,
            bottomPadding = if (state.isUiVisible) {
                if (mediaItems.size > 1) 170.dp else 90.dp
            } else 0.dp,
            alignment = if (state.isUiVisible) Alignment.BottomEnd else Alignment.CenterEnd,
            onVolumeChange = onVolumeChange, onHover = { state.isVolumeHoveredRaw = it }
        )
        if (showInfoDialog && activeItem != null) {
            FileInfoDialog(activeItem, onDismiss = { onShowInfoDialogChange(false) })
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.mediaViewerPlayerModifier(
    state: MediaViewerState,
    videoState: VideoPlayerState?,
    mediaItems: List<MediaItem>,
    mediaVolume: Float,
    isFullscreen: Boolean,
    focusRequester: FocusRequester,
    onClose: (String) -> Unit,
    copyAction: () -> Unit,
    infoAction: () -> Unit,
    onVolumeChange: (Float) -> Unit
): Modifier = this
    .fillMaxSize()
    .clipToBounds()
    .background(if (isFullscreen) Color.Black else IrisTheme.colors.background)
    .onPointerEvent(PointerEventType.Move) {
        state.lastInteractionTime = System.currentTimeMillis()
        state.isUiVisible = true
    }
    .focusRequester(focusRequester)
    .focusable()
    .mediaViewerKeyHandler(
        currentId = state.currentId,
        mediaItems = mediaItems,
        videoState = videoState,
        mediaVolume = mediaVolume,
        onClose = { onClose(state.currentId) },
        onSkip = { dir -> state.skipDirection = dir; state.skipTrigger = System.currentTimeMillis() },
        onNavigate = { dir, newId -> state.animationDirection = dir; state.currentId = newId },
        onVolumeChange = onVolumeChange,
        onPlayPause = { state.playPauseTrigger = System.currentTimeMillis() },
        onCopy = copyAction,
        onInfo = infoAction
    )
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) if (awaitPointerEvent().type == PointerEventType.Press) focusRequester.requestFocus()
        }
    }
