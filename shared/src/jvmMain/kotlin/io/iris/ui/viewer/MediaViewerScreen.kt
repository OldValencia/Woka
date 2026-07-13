package io.iris.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.iris.data.MediaItem
import io.iris.ui.theme.IrisTheme
import io.iris.ui.viewer.player.rememberVideoPlayerState
import kotlinx.coroutines.delay
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MediaViewerScreen(
    initialMediaId: String,
    mediaItems: List<MediaItem>,
    onClose: () -> Unit,
    loadThumbnail: suspend (MediaItem) -> ImageBitmap?
) {
    val state = rememberMediaViewerState(initialMediaId)
    val activeItem = remember(state.currentId, mediaItems) {
        mediaItems.find { it.file.absolutePath == state.currentId }
    }
    
    LaunchedEffect(activeItem) {
        if (activeItem != null) {
            state.imageBitmap = loadThumbnail(activeItem)
            state.activeItemPath = activeItem.file.absolutePath
            state.scale = 1f; state.offsetX = 0f; state.offsetY = 0f
        } else {
            state.activeItemPath = null
        }
    }

    var mediaVolume by remember { mutableStateOf(0.5f) }
    val videoState = if (activeItem is MediaItem.Video && state.activeItemPath != null) {
        rememberVideoPlayerState(File(state.activeItemPath!!), mediaVolume)
    } else null
    
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.isVolumeHoveredRaw) {
        if (state.isVolumeHoveredRaw) state.isVolumeHovered = true
        else { delay(300.milliseconds); state.isVolumeHovered = false }
    }
    LaunchedEffect(state.volumeTrigger) {
        if (state.volumeTrigger > 0) { state.volumeTriggerTimer = 1; delay(2000.milliseconds); state.volumeTriggerTimer = 0 }
    }
    LaunchedEffect(mediaVolume) { videoState?.updateVolume(mediaVolume) }
    LaunchedEffect(state.lastInteractionTime, videoState?.isPlaying) {
        if (videoState?.isPlaying == true) { delay(2000.milliseconds); state.isUiVisible = false }
        else state.isUiVisible = true
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(IrisTheme.colors.background)
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
                onClose = onClose,
                onSkip = { dir -> state.skipDirection = dir; state.skipTrigger = System.currentTimeMillis() },
                onNavigate = { dir, newId -> state.animationDirection = dir; state.currentId = newId },
                onVolumeChange = { vol -> mediaVolume = vol; state.volumeTrigger = System.currentTimeMillis() },
                onPlayPause = { state.playPauseTrigger = System.currentTimeMillis() }
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) if (awaitPointerEvent().type == PointerEventType.Press) focusRequester.requestFocus()
                }
            }
    ) {
        if (state.imageBitmap != null) {
            MediaViewerContent(
                imageBitmap = state.imageBitmap!!, animationDirection = state.animationDirection,
                scale = state.scale, offsetX = state.offsetX, offsetY = state.offsetY, videoState = videoState,
                onScaleChange = { state.scale = it }, onOffsetChange = { x, y -> state.offsetX = x; state.offsetY = y },
                onPlayPause = { state.playPauseTrigger = System.currentTimeMillis() }
            )

            MediaViewerToolbar(
                currentId = state.currentId, mediaItems = mediaItems, imageBitmap = state.imageBitmap!!,
                isVisible = state.isUiVisible, scale = state.scale, offsetX = state.offsetX, offsetY = state.offsetY,
                onScaleChange = { state.scale = it }, onOffsetReset = { state.offsetX = 0f; state.offsetY = 0f; state.scale = 1f },
                onClose = onClose,
                onEdit = { onClose() },
                onPrev = {
                    val idx = mediaItems.indexOfFirst { it.file.absolutePath == state.currentId }
                    if (idx > 0) { state.animationDirection = -1; state.currentId = mediaItems[idx - 1].file.absolutePath }
                },
                onNext = {
                    val idx = mediaItems.indexOfFirst { it.file.absolutePath == state.currentId }
                    if (idx in 0 until mediaItems.lastIndex) { state.animationDirection = 1; state.currentId = mediaItems[idx + 1].file.absolutePath }
                },
                onSelectMedia = { state.animationDirection = 0; state.currentId = it },
                onVolumeHoverChange = { state.isVolumeHoveredRaw = it },
                loadThumbnail = loadThumbnail,
                mediaVolume = mediaVolume,
                onVolumeChange = { mediaVolume = it },
                videoState = videoState
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        
        PlayPauseOverlay(trigger = state.playPauseTrigger, isPlaying = videoState?.isPlaying == true)
        SkipOverlay(trigger = state.skipTrigger, direction = state.skipDirection)

        VolumeVerticalOverlay(
            isVisible = state.isVolumeHovered || state.volumeTriggerTimer > 0,
            volume = mediaVolume,
            bottomPadding = if (state.isUiVisible) { if (mediaItems.size > 1) 170.dp else 90.dp } else 0.dp,
            alignment = if (state.isUiVisible) Alignment.BottomEnd else Alignment.CenterEnd,
            onVolumeChange = { mediaVolume = it }, onHover = { state.isVolumeHoveredRaw = it }
        )
    }
}
