package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.woka.app.AppDependencies
import com.woka.core.data.MediaItem
import com.woka.core.data.WokaSettings
import com.woka.feature.viewer.audio.AudioSession
import com.woka.feature.viewer.pip.PipManager
import com.woka.feature.viewer.video.VideoAlreadyOpenedDialog

/**
 * Loading the active item — the half of [MediaViewerScreen]'s effects that is about
 * *which picture*, as opposed to the ambient ones beside it in `MediaViewerAmbient.kt`.
 *
 * Split in two when keying the decode on the HDR view mode put the file over the size
 * mandate (ME-180); the two halves never shared a line.
 */

/** Loads the active item, restores its size state, and guards the PiP conflict. */
@Composable
internal fun MediaViewerActiveItemEffects(
    state: MediaViewerState,
    activeItem: MediaItem?,
    settings: WokaSettings,
    showConflictDialog: Boolean,
    onConflictChange: (Boolean) -> Unit,
    onClose: (String) -> Unit,
    thumbnails: ViewerThumbnails
) {
    LaunchedEffect(activeItem) {
        val path = activeItem?.file?.absolutePath
        val music = AudioSession.current?.file?.absolutePath
        if (pipConflicts(activeItem, PipManager.isPipActive, PipManager.activeItem?.file?.absolutePath) ||
            musicConflicts(activeItem, music)
        ) {
            onConflictChange(true)
            return@LaunchedEffect
        }
        if (activeItem == null) {
            state.activeItemPath = null
            return@LaunchedEffect
        }
        // This is the gate the video player is created on, so it is set here and
        // the decode is left to the effect below — awaiting one first would delay
        // playback by however long it takes (F2: both effects used to decode, so
        // every file change loaded its bitmap twice).
        state.activeItemPath = path
        // Before the decode below is keyed on it, and with no suspension in
        // between, so the load never asks for frame 4 of a file that has one.
        state.frameIndex = 0
        // An animation opens playing, looping, at 1×; a photograph never asks (RD-501).
        state.isFramePlaying = true
        state.isFrameLooping = true
        state.frameSpeed = 1f
        // Returning from fullscreen (PiP) is the same file: carry the size state
        // back instead of resetting it to the global preset.
        val fromPip = PipManager.isTransitioningToMain &&
            PipManager.activeItem?.file?.absolutePath == path
        if (fromPip) {
            state.sizeMode = PipManager.sizeMode
            state.offsetX = PipManager.offsetX
            state.offsetY = PipManager.offsetY
        } else {
            state.sizeMode = startSizeMode(settings)
            state.offsetX = 0f
            state.offsetY = 0f
        }
    }

    // Now Playing's bubble hides over its own file (AP-006).
    val nowPlaying = remember { AppDependencies.nowPlaying }
    LaunchedEffect(state.activeItemPath) { nowPlaying.shownInViewer = state.activeItemPath }
    DisposableEffect(Unit) { onDispose { nowPlaying.shownInViewer = null } }

    if (showConflictDialog) {
        VideoAlreadyOpenedDialog(
            onConfirm = {
                // Only what is up: with no PiP, `closePip` would dispose the viewer's own player.
                if (PipManager.isPipActive) PipManager.closePip()
                AudioSession.stopForVideo()
                onConflictChange(false)
                if (activeItem != null) {
                    state.activeItemPath = activeItem.file.absolutePath
                    state.sizeMode = startSizeMode(settings)
                    state.offsetX = 0f
                    state.offsetY = 0f
                }
            },
            onDismiss = {
                onConflictChange(false)
                onClose(state.currentId)
            },
            music = AudioSession.current != null,
            toMusic = activeItem is MediaItem.Audio
        )
    }

    // The decode, for both paths — `MediaViewerStillLoad.kt`.
    StillLoadEffect(state, activeItem, settings, showConflictDialog, thumbnails)
}
