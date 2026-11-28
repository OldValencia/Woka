package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.woka.core.data.MediaItem
import com.woka.core.ui.image.AppliedStillEdits
import com.woka.core.ui.image.AppliedVideoEdits
import com.woka.core.ui.image.rememberReloadKey
import com.woka.core.ui.components.OriginalCompare

/**
 * Whether [item] carries an edit kept by `Done` — the carousel's dot and the pill's
 * `Original` ask the same question (RD-502).
 *
 * **Per kind, because the two media keep their edits differently.** A still is asked
 * through [AppliedStillEdits.carries], a few kilobytes off its head, re-read on the reload
 * key a save bumps (ME-135). A film's edit is already held by [AppliedVideoEdits], re-read
 * when its `generation` moves, as `MediaViewerFrame` reads it. A track has no editor.
 *
 * False until a still's read lands: no mark is better than a mark that may be wrong
 * (`rememberEditedFrames`).
 */
@Composable
internal fun rememberCarriesEdit(item: MediaItem?): Boolean {
    val path = item?.file?.absolutePath
    val reloadKey = rememberReloadKey(path)
    val generation = AppliedVideoEdits.generation
    var stillCarries by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path, reloadKey) {
        stillCarries = item is MediaItem.Image && AppliedStillEdits.carries(item.file)
    }
    return when (item) {
        is MediaItem.Image -> stillCarries
        is MediaItem.Video -> remember(path, generation) { AppliedVideoEdits.editOf(item.file) != null }
        else -> false
    }
}

/** `Original`, while the shown file offers it; null otherwise — the pill and both key paths. */
internal fun MediaViewerState.offeredOriginal(): OriginalCompare? = original.takeIf { canShowOriginal }

/**
 * Publishes whether the shown file offers `Original`, and ends a comparison when it stops
 * applying: another file (the prototype's `setScene`), or an edit reverted under it.
 *
 * Fullscreen needs nothing here: it is PiP's own window, and entering it closes this viewer
 * (`enterFullscreen`), comparison and all.
 */
@Composable
internal fun ViewerOriginalEffect(state: MediaViewerState, activeItem: MediaItem?) {
    val carries = rememberCarriesEdit(activeItem)
    LaunchedEffect(activeItem?.file?.absolutePath) { state.original.end() }
    LaunchedEffect(carries) {
        state.canShowOriginal = carries
        if (!carries) state.original.end()
    }
}
