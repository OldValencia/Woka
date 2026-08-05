package com.woka.feature.viewer.shell

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
import com.woka.core.data.MediaItem
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.skipBy
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
    onZoom: (ZoomKey) -> Unit
): Modifier = this.onKeyEvent { event ->
    handleMediaViewerKeyEvent(
        event, currentId, mediaItems, videoState, mediaVolume,
        onClose, onSkip, onNavigate, onVolumeChange, onPlayPause, onCopy, onInfo, onZoom
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
    onZoom: (ZoomKey) -> Unit
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
                        it.skipBy(-5000)
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
                        it.skipBy(5000)
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
        else -> handleZoomKeyEvent(event, onZoom)
    }
}

/**
 * `Ctrl +` / `Ctrl −` / `Ctrl 0` (Fit) / `Ctrl 1` (Actual) — the standard set,
 * absent until Step 8b.
 */
private fun handleZoomKeyEvent(event: KeyEvent, onZoom: (ZoomKey) -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (!event.isCtrlPressed && !event.isMetaPressed) return false
    val intent = when (event.key) {
        Key.Equals, Key.Plus, Key.NumPadAdd -> ZoomKey.In
        Key.Minus, Key.NumPadSubtract -> ZoomKey.Out
        Key.Zero, Key.NumPad0 -> ZoomKey.Fit
        Key.One, Key.NumPad1 -> ZoomKey.Actual
        else -> return false
    }
    onZoom(intent)
    return true
}
