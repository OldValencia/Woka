package com.woka.ui.viewer

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.woka.data.MediaItem
import com.woka.ui.viewer.player.VideoPlayerState
import com.woka.ui.viewer.player.seek
import com.woka.ui.viewer.player.togglePlayPause

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
    onInfo: () -> Unit
): Modifier = this.onKeyEvent { event ->
    handleMediaViewerKeyEvent(
        event, currentId, mediaItems, videoState, mediaVolume,
        onClose, onSkip, onNavigate, onVolumeChange, onPlayPause, onCopy, onInfo
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
    onInfo: () -> Unit
): Boolean {
    return when (event.key) {
        Key.Escape -> {
            if (event.type == KeyEventType.KeyDown) {
                onClose()
            }
            true
        }
        Key.DirectionLeft -> {
            if (event.type == KeyEventType.KeyDown) {
                if (event.isAltPressed) {
                    videoState?.let { 
                        it.seek((it.currentPositionMs - 5000).coerceAtLeast(0)) 
                        onSkip(-1)
                    }
                } else {
                    val idx = mediaItems.indexOfFirst { it.file.absolutePath == currentId }
                    if (idx > 0) {
                        onNavigate(-1, mediaItems[idx - 1].file.absolutePath)
                    }
                }
            }
            true
        }
        Key.DirectionRight -> {
            if (event.type == KeyEventType.KeyDown) {
                if (event.isAltPressed) {
                    videoState?.let { 
                        it.seek((it.currentPositionMs + 5000).coerceAtMost(it.durationMs)) 
                        onSkip(1)
                    }
                } else {
                    val idx = mediaItems.indexOfFirst { it.file.absolutePath == currentId }
                    if (idx in 0 until mediaItems.lastIndex) {
                        onNavigate(1, mediaItems[idx + 1].file.absolutePath)
                    }
                }
            }
            true
        }
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
        else -> false
    }
}
