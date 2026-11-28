package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.woka.core.data.MediaItem
import com.woka.app.shell.GlobalShortcutState
import com.woka.app.shell.LocalGlobalShortcuts
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.rememberResultClock

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
    // A cut film steps on the result's timeline, as its slider reads (RD-501).
    val curClock by rememberUpdatedState(rememberResultClock(videoState))

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
                soundBoost = curBoost,
                onFrameKey = { dir -> state.stepOneFrame(curVideoState, curClock, dir) },
                original = state.offeredOriginal()
            )
        }
        onDispose { globalShortcuts.onPreviewKey = previous }
    }
}

/**
 * Both of the viewer's key paths: the window-wide preview hook above and the focused player's
 * own handler, which take the same actions. One call, split out of `MediaViewerScreen` when
 * RD-501 needed a line there; [setVolume] is the bare write, each path adds its own flash.
 */
@Composable
internal fun viewerKeyedModifier(
    state: MediaViewerState,
    mediaItems: List<MediaItem>,
    videoState: VideoPlayerState?,
    mediaVolume: Float,
    isFullscreen: Boolean,
    focusRequester: FocusRequester,
    onClose: (String) -> Unit,
    copyAction: () -> Unit,
    infoAction: () -> Unit,
    onSizeModeChange: (SizeMode) -> Unit,
    setVolume: (Float) -> Unit,
    onToggleFullscreen: () -> Unit,
    soundBoost: Boolean
): Modifier {
    MediaViewerShortcutEffect(
        state = state,
        mediaItems = mediaItems,
        videoState = videoState,
        mediaVolume = mediaVolume,
        globalShortcuts = LocalGlobalShortcuts.current,
        onClose = onClose,
        onVolumeChange = setVolume,
        onCopy = { copyAction() },
        onInfo = { infoAction() },
        onSizeModeChange = onSizeModeChange,
        onToggleFullscreen = onToggleFullscreen,
        soundBoost = soundBoost
    )
    return Modifier.mediaViewerPlayerModifier(
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
        onVolumeChange = { vol -> setVolume(vol); state.volumeEvent.raise() },
        onToggleFullscreen = onToggleFullscreen,
        soundBoost = soundBoost
    )
}
