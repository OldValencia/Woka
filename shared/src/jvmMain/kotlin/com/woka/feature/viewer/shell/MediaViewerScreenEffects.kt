package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
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
    thumbnails: ViewerThumbnails
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
        // This is the gate the video player is created on, so it is set here and
        // the decode is left to the effect below — awaiting one first would delay
        // playback by however long it takes (F2: both effects used to decode, so
        // every file change loaded its bitmap twice).
        state.activeItemPath = path
        // Before the decode below is keyed on it, and with no suspension in
        // between, so the load never asks for frame 4 of a file that has one.
        state.frameIndex = 0
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

    // The decode, for both paths: a plain file change, and the reload after the
    // PiP conflict above is confirmed and `showConflictDialog` goes false.
    //
    // **Two stages, on purpose.** The full decode of a 36-megapixel HEIC is a
    // quarter of a second plus a 138 MB texture upload, and until it landed the
    // *previous* file stayed on screen — paging looked like a freeze and then a
    // jump. The preview is the one the carousel already built, so it is a disk
    // read or a cache hit, and `ContentScale.Fit` draws it in exactly the
    // rectangle the full picture will occupy. `sourceSize` is read first so the
    // zoom presets mean native pixels from the very first paint.
    //
    // Keyed on the frame too, so stepping through a burst goes through the same
    // two stages as opening a file does.
    LaunchedEffect(activeItem, showConflictDialog, state.frameIndex) {
        if (showConflictDialog || activeItem == null) return@LaunchedEffect
        val frame = state.frameIndex
        state.sourceSize = thumbnails.sourceSize(activeItem)
        val alreadyDecoded = thumbnails.cachedFull(activeItem, frame)
        if (alreadyDecoded != null) {
            // Going back to a picture still in the cache: showing the preview
            // first would be a frame of blur bought for nothing.
            state.imageBitmap = alreadyDecoded
            return@LaunchedEffect
        }
        thumbnails.preview(activeItem, frame)?.let { state.imageBitmap = it }
        // Null means this picture could not be decoded — keep whatever is on
        // screen rather than blanking it, which is what a frame index left over
        // from the previous file would otherwise do for one composition.
        thumbnails.full(activeItem, frame)?.let { state.imageBitmap = it }
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
