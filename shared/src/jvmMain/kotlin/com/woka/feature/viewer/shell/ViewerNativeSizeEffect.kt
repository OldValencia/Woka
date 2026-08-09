package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Re-fits the pan offset whenever the media's own pixel dimensions change under it.
 *
 * A pan offset is a distance from centre in pixels, and what bounds it is the
 * frame's size — see [clampOffset] and [panLimits]. Every other route that moves
 * those bounds re-clamps: the end of a drag, and a zoom change. Switching video
 * track did not, because it changes the bounds without touching either. A picture
 * panned to the edge of a 3840x2160 stream therefore stayed shoved that far off
 * centre on a stream of a different size, with no way back but panning by hand.
 *
 * Clamping rather than zeroing is what makes it *adapt*: two tracks of the same
 * size leave the view exactly where the user put it, and a track too small to
 * overflow the panel re-centres on its own, because [panLimits] is zero there.
 *
 * The size mode needs nothing done to it. `SizeMode.Preset` re-resolves against
 * the new native size on every composition already (`computeGraphicsScale`), and a
 * `SizeMode.Zoom` percentage is a statement about native 1:1 pixels that stays
 * true whichever track supplies them.
 */
@Composable
internal fun ViewerNativeSizeEffect(state: MediaViewerState) {
    LaunchedEffect(state.nativeSize) {
        val (x, y) = clampOffset(
            state.offsetX,
            state.offsetY,
            state.sizeMode,
            state.containerSize,
            state.nativeSize
        )
        state.offsetX = x
        state.offsetY = y
    }
}
