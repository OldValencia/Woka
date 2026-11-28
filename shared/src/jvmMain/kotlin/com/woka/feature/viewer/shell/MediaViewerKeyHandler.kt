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
import com.woka.core.system.layoutFreeKey
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.togglePlayPause
import com.woka.core.data.volumeCeiling
import com.woka.feature.viewer.video.engine.toggleMute
import com.woka.core.ui.components.OriginalCompare
import com.woka.core.ui.components.holdOnKey

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
    onStepFrame: (Int) -> Unit,
    onToggleFullscreen: () -> Unit,
    soundBoost: Boolean = false,
    onFrameKey: (Int) -> Boolean = { false },
    /** Read per key, so a file that gains or loses its edit is answered as it is now (RD-502). */
    original: () -> OriginalCompare? = { null }
): Modifier = this.onKeyEvent { event ->
    handleMediaViewerKeyEvent(
        event, currentId, mediaItems, videoState, mediaVolume,
        onClose, onSkip, onNavigate, onVolumeChange, onPlayPause, onCopy, onInfo, onZoom,
        onStepFrame, onToggleFullscreen, soundBoost, onFrameKey, original()
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
    onStepFrame: (Int) -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    /** So ↑/↓ stop where the slider stops, rather than at 100 % regardless. */
    soundBoost: Boolean = false,
    /** `,` / `.` (RD-501): answers whether a frame was stepped, so a playing film lets the key go. */
    onFrameKey: (Int) -> Boolean = { false },
    /** `Original`, when the file offers it: `\` holds it, Esc ends it before closing (RD-502). */
    original: OriginalCompare? = null
): Boolean {
    val ceiling = volumeCeiling(soundBoost)
    if (original?.holdOnKey(event) == true) return true
    // `,` / `.` by the key, not by what the layout types on it (RD-501).
    return when (event.layoutFreeKey()) {
        Key.Escape -> {
            if (event.type == KeyEventType.KeyDown && original?.endIfActive() != true) onClose()
            true
        }
        Key.DirectionLeft ->
            handleArrowKey(event, currentId, mediaItems, videoState, -1, onSkip, onNavigate)
        Key.DirectionRight ->
            handleArrowKey(event, currentId, mediaItems, videoState, 1, onSkip, onNavigate)
        Key.PageUp, Key.PageDown -> handleFrameKey(event, onStepFrame)
        Key.Comma, Key.Period -> handleOneFrameKey(event, onFrameKey)
        Key.DirectionUp -> {
            if (event.type == KeyEventType.KeyDown && videoState?.hasAudio != false) {
                onVolumeChange((mediaVolume + 0.1f).coerceIn(0f, ceiling))
            }
            true
        }
        Key.DirectionDown -> {
            if (event.type == KeyEventType.KeyDown && videoState?.hasAudio != false) {
                onVolumeChange((mediaVolume - 0.1f).coerceIn(0f, ceiling))
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
        // Bare, like Space: Ctrl+F is a find shortcut everywhere else and must not
        // be swallowed here.
        Key.F -> {
            if (event.type == KeyEventType.KeyDown && !event.isCtrlPressed && !event.isMetaPressed) {
                onToggleFullscreen()
                true
            } else false
        }
        Key.M -> {
            if (event.type == KeyEventType.KeyDown && !event.isCtrlPressed && !event.isMetaPressed &&
                videoState != null && videoState.hasAudio
            ) {
                videoState.toggleMute(mediaVolume, onVolumeChange)
                true
            } else false
        }
        else -> handleZoomKeyEvent(event, onZoom)
    }
}
