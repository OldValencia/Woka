package com.woka.feature.viewer.shell

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.woka.core.data.MediaItem
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.togglePlayPause

fun Modifier.mediaViewerKeyHandler(
    currentId: String,
    mediaItems: List<MediaItem>,
    videoState: VideoPlayerState?,
    mediaVolume: Float,
    onClose: () -> Unit,
    onSkip: (direction: Int) -> Unit,
    onNavigate: (direction: Int, newId: String) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onCopy: () -> Unit,
    onInfo: () -> Unit,
    onZoom: (ZoomKey) -> Unit,
    onStepFrame: (Int) -> Unit
): Modifier = this.onKeyEvent { event ->
    handleMediaViewerKeyEvent(
        event, currentId, mediaItems, videoState, mediaVolume,
        onClose, onSkip, onNavigate, onVolumeChange, onPlayPause, onCopy, onInfo, onZoom,
        onStepFrame
    )
}

/**
 * Shared media-viewer shortcut logic. Used both by [mediaViewerKeyHandler] (on
 * the focused player) and by the global preview hook in the shell, so shortcuts
 * work regardless of which element currently holds focus. Returns true when the
 * event is handled/consumed.
 */
fun handleMediaViewerKeyEvent(
    event: KeyEvent,
    currentId: String,
    mediaItems: List<MediaItem>,
    videoState: VideoPlayerState?,
    mediaVolume: Float,
    onClose: () -> Unit,
    onSkip: (direction: Int) -> Unit,
    onNavigate: (direction: Int, newId: String) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onCopy: () -> Unit,
    onInfo: () -> Unit,
    onZoom: (ZoomKey) -> Unit,
    onStepFrame: (Int) -> Unit = {}
): Boolean {
    return when (event.key) {
        Key.Escape -> {
            if (event.type == KeyEventType.KeyDown) onClose()
            true
        }
        Key.DirectionLeft ->
            handleArrowKey(event, currentId, mediaItems, videoState, -1, onSkip, onNavigate)
        Key.DirectionRight ->
            handleArrowKey(event, currentId, mediaItems, videoState, 1, onSkip, onNavigate)
        Key.PageUp, Key.PageDown -> handleFrameKey(event, onStepFrame)
        Key.DirectionUp -> {
            if (event.type == KeyEventType.KeyDown && videoState?.hasAudio != false) {
                onVolumeChange((mediaVolume + 0.1f).coerceIn(0f, 1f))
            }
            true
        }
        Key.DirectionDown -> {
            if (event.type == KeyEventType.KeyDown && videoState?.hasAudio != false) {
                onVolumeChange((mediaVolume - 0.1f).coerceIn(0f, 1f))
            }
            true
        }
        Key.Spacebar -> {
            if (event.type == KeyEventType.KeyDown) {
                videoState?.togglePlayPause()
                onPlayPause()
            }
            true
        }
        Key.C -> {
            if (event.type == KeyEventType.KeyDown && (event.isCtrlPressed || event.isMetaPressed)) {
                onCopy()
                true
            } else false
        }
        Key.I -> {
            if (event.type == KeyEventType.KeyDown && (event.isCtrlPressed || event.isMetaPressed)) {
                onInfo()
                true
            } else false
        }
        else -> handleZoomKeyEvent(event, onZoom)
    }
}
