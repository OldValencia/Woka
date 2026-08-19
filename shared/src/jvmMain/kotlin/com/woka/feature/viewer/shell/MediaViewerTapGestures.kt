package com.woka.feature.viewer.shell

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.woka.core.data.ScalePreset
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.togglePlayPause

/**
 * Tap handling, split out of [mediaViewerGestures] per the §2 size mandate: tap
 * toggles play/pause on a video, double-tap goes fullscreen on a video or doubles
 * the zoom around the cursor on an image.
 */
internal fun Modifier.mediaTapGestures(
    videoState: VideoPlayerState?,
    mode: () -> SizeMode,
    container: () -> IntSize,
    native: () -> IntSize,
    offset: () -> Pair<Float, Float>,
    onSizeModeChange: (SizeMode) -> Unit,
    onOffsetChange: (Float, Float) -> Unit,
    onZoomHud: () -> Unit,
    onPlayPause: () -> Unit,
    onToggleFullscreen: () -> Unit
): Modifier = this.pointerInput(videoState) {
    detectTapGestures(
        onDoubleTap = { tap ->
            if (videoState != null) {
                onToggleFullscreen()
                return@detectTapGestures
            }
            val bounds = zoomBounds(container(), native())
            val percent = mode().percentIn(container(), native())
            // Double-tap only ever zooms in; once at the top it returns to Fit
            // rather than leaving the picture stuck against the cap.
            if (percent >= bounds.last) {
                onSizeModeChange(SizeMode.Preset(ScalePreset.Fit))
                onOffsetChange(0f, 0f)
            } else {
                val (x, y) = offset()
                val result = zoomTo(percent * 2, tap, mode(), container(), native(), x, y)
                onOffsetChange(result.offsetX, result.offsetY)
                onSizeModeChange(result.sizeMode)
            }
            onZoomHud()
        },
        onTap = {
            // **Raised for a still as well** (ME-140): an animated frame set listens for
            // the same signal the keyboard's Space raises, so a GIF pauses under the
            // gesture people already know. A photograph has nothing listening.
            videoState?.togglePlayPause()
            onPlayPause()
        }
    )
}
