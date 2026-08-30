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
    onSizeModeChange: (SizeMode) -> Unit,
    onToggleFullscreen: () -> Unit,
    soundBoost: Boolean = false
) {
    val curBoost by rememberUpdatedState(soundBoost)
    val curVideoState by rememberUpdatedState(videoState)
    val curMediaItems by rememberUpdatedState(mediaItems)
    val curVolume by rememberUpdatedState(mediaVolume)
    val curCopy by rememberUpdatedState(onCopy)
    val curInfo by rememberUpdatedState(onInfo)
    val curVolumeChange by rememberUpdatedState(onVolumeChange)
    val curSizeModeChange by rememberUpdatedState(onSizeModeChange)
    val curToggleFullscreen by rememberUpdatedState(onToggleFullscreen)

    DisposableEffect(globalShortcuts) {
        // Give back what was found rather than clearing the slot (ME-124), symmetric
        // with `EditorKeyEffect`. Identical today — the viewer is the first claimant, so
        // this is null — and the asymmetry is what made "whoever disposes last wins"
        // possible: a viewer torn down under an editor would wipe the editor's handler.
        val previous = globalShortcuts.onPreviewKey
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
                onZoom = { key -> state.applyZoomKey(key) { curSizeModeChange(it) } },
                onToggleFullscreen = { curToggleFullscreen() },
                soundBoost = curBoost
            )
        }
        onDispose { globalShortcuts.onPreviewKey = previous }
    }
}
