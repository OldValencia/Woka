package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.woka.core.data.MediaItem
import com.woka.core.data.WokaSettings
import com.woka.core.ui.image.rememberReloadKey
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
        if (pipConflicts(activeItem, PipManager.isPipActive, PipManager.activeItem?.file?.absolutePath)) {
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
        // An animation opens playing; a photograph never asks (animated-stills).
        state.isFramePlaying = true
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
    //
    // ...and on the reload key, which is how a save reaches the screen (ME-135). The
    // editor is mounted over a viewer that stays composed, so nothing above moves
    // while an edit is made: `Done` changed nothing until the viewer was re-entered.
    // ...and on the HDR view mode, because since ME-177 that decides what the decoded
    // pixels *are*, not how they are drawn (ME-180). Without it the cache key moved and
    // nothing asked for the new entry: flipping the switch changed the picture only after
    // the file was reopened.
    val reloadKey = rememberReloadKey(activeItem?.file?.absolutePath)
    LaunchedEffect(
        activeItem,
        showConflictDialog,
        state.frameIndex,
        reloadKey,
        settings.hdrViewMode
    ) {
        if (showConflictDialog || activeItem == null) return@LaunchedEffect
        val frame = state.frameIndex
        state.sourceSize = thumbnails.sourceSize(activeItem)
        state.stillColor = thumbnails.stillColor(activeItem)
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
