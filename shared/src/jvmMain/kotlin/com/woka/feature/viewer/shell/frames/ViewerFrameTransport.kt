package com.woka.feature.viewer.shell.frames

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.woka.core.data.ImageFrame
import com.woka.core.ui.components.frames.FrameTransport
import com.woka.feature.viewer.shell.MediaViewerState

/**
 * The viewer's animation, as the transport sees it (RD-501): the same `frameIndex` the strip
 * and `PageUp`/`PageDown` move and the decode is keyed on, so the bar adds no second number.
 */
@Stable
internal class ViewerFrameTransport(
    private val state: MediaViewerState,
    override val frames: List<ImageFrame>
) : FrameTransport {
    override var frameIndex: Int
        get() = state.frameIndex
        set(value) { state.frameIndex = value }
    override var isPlaying: Boolean
        get() = state.isFramePlaying
        set(value) { state.isFramePlaying = value }
    override var loop: Boolean
        get() = state.isFrameLooping
        set(value) { state.isFrameLooping = value }
    override var speed: Float
        get() = state.frameSpeed
        set(value) { state.frameSpeed = value }
    override var showFrameNumbers: Boolean
        get() = state.showFrameNumbers
        set(value) { state.showFrameNumbers = value }
}

/** The transport for an animation, or null for anything the clock does not run. */
@Composable
internal fun rememberViewerFrameTransport(state: MediaViewerState, strip: FrameStripModel): FrameTransport? =
    remember(state, strip) { if (strip.isAnimated) ViewerFrameTransport(state, strip.frames) else null }
