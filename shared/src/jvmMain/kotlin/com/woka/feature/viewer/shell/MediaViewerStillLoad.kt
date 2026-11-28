package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.woka.core.data.MediaItem
import com.woka.core.data.WokaSettings
import com.woka.core.ui.image.rememberReloadKey
import com.woka.feature.viewer.MediaStartup

/**
 * The active item's picture: the preview first, then the full decode (split out of
 * `MediaViewerScreenEffects.kt` at the size mandate, RD-203).
 *
 * **A reload of the picture already on screen skips the preview** (the author, 2026-11-25:
 * leaving the editor blinked). A save evicts the file's cache entries and bumps the reload
 * key; stepping down to the 400 px preview and back up to the full decode was the blink. The
 * picture on screen stays until the new full one replaces it.
 */
@Composable
internal fun StillLoadEffect(
    state: MediaViewerState,
    activeItem: MediaItem?,
    settings: WokaSettings,
    showConflictDialog: Boolean,
    thumbnails: ViewerThumbnails
) {
    val shown = remember { ShownStill() }
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
        settings.hdrViewMode,
        state.original.active
    ) {
        if (showConflictDialog || activeItem == null) return@LaunchedEffect
        val frame = state.frameIndex
        // `Original` (RD-502): the frame as shot replaces the edited one, with no preview step
        // — the edit stays up until the original lands. A film answers null and is compared
        // in its frame (`LocalViewerOriginal`). Leaving it is a reload of the shown key below.
        if (state.original.active) {
            thumbnails.original(activeItem, frame)?.let { state.imageBitmap = it }
            return@LaunchedEffect
        }
        val key = activeItem.file.absolutePath to frame
        // The same picture asked for again — a save's reload — keeps what is on screen.
        val reloading = shown.key == key && state.imageBitmap != null
        // A track with no cover must not show the previous file's picture as its own.
        if (activeItem is MediaItem.Audio) state.imageBitmap = null
        val early = MediaStartup.takeStill(activeItem, frame) // a direct open's, unsuspended: frame one
        state.sourceSize = early?.size ?: thumbnails.sourceSize(activeItem)
        state.stillColor = early?.color ?: thumbnails.stillColor(activeItem)
        val alreadyDecoded = thumbnails.cachedFull(activeItem, frame)
        if (alreadyDecoded != null) {
            // Going back to a picture still in the cache: showing the preview
            // first would be a frame of blur bought for nothing.
            state.imageBitmap = alreadyDecoded
            shown.key = key
            return@LaunchedEffect
        }
        if (!reloading) (early?.preview ?: thumbnails.preview(activeItem, frame))?.let { state.imageBitmap = it }
        // Null means this picture could not be decoded — keep whatever is on
        // screen rather than blanking it, which is what a frame index left over
        // from the previous file would otherwise do for one composition.
        thumbnails.full(activeItem, frame)?.let {
            state.imageBitmap = it
            shown.key = key
        }
    }
}

/** Which file and frame the picture on screen is the full decode of. */
private class ShownStill {
    var key: Pair<String, Int>? = null
}
