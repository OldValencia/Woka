package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ImageBitmap
import com.woka.core.data.MediaItem
import com.woka.core.data.SettingsRepository
import com.woka.core.data.WokaSettings
import com.woka.feature.viewer.pip.PipManager
import com.woka.feature.viewer.video.engine.VideoPlayerState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import com.woka.feature.viewer.video.VideoAlreadyOpenedDialog

/**
 * Side effects of [MediaViewerScreen], split out per the §2 file-size mandate so
 * the screen itself stays a composition of state, effects and layout.
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
    loadThumbnail: suspend (MediaItem) -> ImageBitmap?
) {
    LaunchedEffect(activeItem) {
        val path = activeItem?.file?.absolutePath
        if (PipManager.isPipActive && path != null && path != PipManager.activeItem?.file?.absolutePath) {
            onConflictChange(true)
            return@LaunchedEffect
        }
        if (activeItem == null) {
            state.activeItemPath = null
            return@LaunchedEffect
        }
        // Assigned before the thumbnail loads, not after: this is the gate the
        // video player is created on, and awaiting a decode first would delay
        // playback by however long that decode takes.
        state.activeItemPath = path
        state.imageBitmap = loadThumbnail(activeItem)
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

    if (showConflictDialog) {
        VideoAlreadyOpenedDialog(
            onConfirm = {
                PipManager.closePip()
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
            }
        )
    }

    LaunchedEffect(activeItem, showConflictDialog) {
        if (!showConflictDialog && activeItem != null) {
            state.imageBitmap = loadThumbnail(activeItem)
        }
    }
}

/** Volume persistence, the volume HUD timer, UI auto-hide and initial focus. */
@Composable
internal fun MediaViewerAmbientEffects(
    state: MediaViewerState,
    videoState: VideoPlayerState?,
    mediaVolume: Float,
    settingsRepo: SettingsRepository,
    focusRequester: FocusRequester
) {
    PopoverHoverEffect(state.hover)
    LaunchedEffect(mediaVolume) {
        settingsRepo.setMediaVolume(mediaVolume)
        videoState?.updateVolume(mediaVolume)
    }
    LaunchedEffect(state.lastInteractionTime, videoState?.isPlaying, state.hover.rawTarget) {
        if (videoState?.isPlaying == false || state.hover.rawTarget != HoverTarget.None) {
            state.isUiVisible = true
        } else {
            delay(2500.milliseconds)
            state.isUiVisible = false
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
