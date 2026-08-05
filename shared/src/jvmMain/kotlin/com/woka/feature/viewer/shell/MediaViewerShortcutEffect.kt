package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.woka.core.data.MediaItem
import com.woka.app.shell.GlobalShortcutState
import com.woka.feature.viewer.video.engine.VideoPlayerState

/**
 * Registers the viewer shortcuts app-wide so they fire in the key-event preview
 * phase — before any focused button gets the key. Space, arrows and Esc then work
 * no matter what holds focus, and stop also activating the focused button. Fresh
 * values are read through [rememberUpdatedState], so this registers only once.
 */
@Composable
internal fun MediaViewerShortcutEffect(
    state: MediaViewerState,
    mediaItems: List<MediaItem>,
    videoState: VideoPlayerState?,
    mediaVolume: Float,
    globalShortcuts: GlobalShortcutState,
    onClose: (String) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onCopy: () -> Unit,
    onInfo: () -> Unit,
    onSizeModeChange: (SizeMode) -> Unit
) {
    val curVideoState by rememberUpdatedState(videoState)
    val curMediaItems by rememberUpdatedState(mediaItems)
    val curVolume by rememberUpdatedState(mediaVolume)
    val curCopy by rememberUpdatedState(onCopy)
    val curInfo by rememberUpdatedState(onInfo)
    val curVolumeChange by rememberUpdatedState(onVolumeChange)
    val curSizeModeChange by rememberUpdatedState(onSizeModeChange)

    DisposableEffect(globalShortcuts) {
        globalShortcuts.onPreviewKey = { event ->
            handleMediaViewerKeyEvent(
                event = event,
                currentId = state.currentId,
                mediaItems = curMediaItems,
                videoState = curVideoState,
                mediaVolume = curVolume,
                onClose = { onClose(state.currentId) },
                onSkip = { dir ->
                    state.skipDirection = dir
                    state.skipEvent.raise()
                },
                onNavigate = { dir, newId ->
                    state.animationDirection = dir
                    state.currentId = newId
                },
                onVolumeChange = { vol ->
                    curVolumeChange(vol)
                    state.volumeEvent.raise()
                },
                onPlayPause = { state.playPauseEvent.raise() },
                onCopy = { curCopy() },
                onInfo = { curInfo() },
                onZoom = { key -> state.applyZoomKey(key) { curSizeModeChange(it) } }
            )
        }
        onDispose { globalShortcuts.onPreviewKey = null }
    }
}
