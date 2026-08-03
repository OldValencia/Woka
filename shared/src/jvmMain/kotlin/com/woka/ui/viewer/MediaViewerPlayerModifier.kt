package com.woka.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import com.woka.data.MediaItem
import com.woka.ui.theme.Theme
import com.woka.ui.viewer.player.VideoPlayerState

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
    onSizeModeChange: (SizeMode) -> Unit,
    onVolumeChange: (Float) -> Unit
): Modifier = this
    .fillMaxSize()
    .clipToBounds()
    .background(if (isFullscreen) Color.Black else Theme.colors.background)
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
        onInfo = infoAction,
        onZoom = { key -> state.applyZoomKey(key, onSizeModeChange) }
    )
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) if (awaitPointerEvent().type == PointerEventType.Press) focusRequester.requestFocus()
        }
    }
