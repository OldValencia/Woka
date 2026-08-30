package com.woka.feature.viewer.shell

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.core.data.MediaItem
import com.woka.feature.viewer.shell.frames.FramePauseRequestEffect
import com.woka.feature.viewer.shell.frames.FramePlaybackEffect
import com.woka.core.ui.components.frames.FrameStrip
import com.woka.core.ui.components.frames.rememberEditedFrames
import com.woka.feature.viewer.shell.frames.FrameStripModel

/**
 * Everything in the player that is about a file holding **more than one picture**: the
 * published frame count, the clock that plays an animation, the play/pause request the
 * clock answers, and the strip down the left edge.
 *
 * Split out of [MediaViewerPlayer] when the clock arrived and pushed that file past the
 * size mandate — the same cut [MediaViewerPlayerOverlays] made before it, and for the
 * same reason: none of this has anything to do with laying the player out.
 *
 * **A photograph still composes the tree it always did.** Both effects return before
 * doing anything for a set with one frame or no timings, and [showStrip] is false, so
 * what a single picture adds here is two comparisons.
 */
@Composable
internal fun BoxScope.MediaViewerPlayerFrames(
    state: MediaViewerState,
    strip: FrameStripModel,
    activeItem: MediaItem?,
    thumbnails: ViewerThumbnails,
    showStrip: Boolean,
    bottomPadding: Dp,
    isFrozen: Boolean = false
) {
    // Only for a file that has more than one picture in it, so an ordinary
    // photograph composes byte for byte the tree it always did.
    LaunchedEffect(strip) { state.frameCount = strip.frames.size.coerceAtLeast(1) }
    FramePlaybackEffect(state, strip, isFrozen)
    FramePauseRequestEffect(state, strip)

    if (activeItem == null || !showStrip) return

    FrameStrip(
        item = activeItem,
        frames = strip.frames,
        selectedIndex = state.frameIndex,
        loadPreview = { item, frame -> thumbnails.preview(item, frame) },
        onSelectFrame = {
            // Reaching for a frame by hand is asking the animation to stand
            // still; one that walked away from the frame just chosen would be
            // the only surprising thing here.
            state.isFramePlaying = false
            state.frameIndex = it
        },
        // Which frames carry an edit of their own (ME-157). A burst is a place where
        // an edit can hide: five thumbnails, one of them following a rule of its own,
        // and nothing on screen saying which.
        editedFrames = rememberEditedFrames(activeItem.file.absolutePath),
        modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = 12.dp, top = 72.dp, bottom = bottomPadding)
    )
}
