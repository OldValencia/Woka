package com.woka.feature.viewer.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import com.woka.feature.viewer.pip.PipManager
import com.woka.feature.viewer.video.engine.VideoPlayerState

/**
 * The media surface of the fullscreen window: [MediaViewerContent] bound to the
 * [PipManager] state that survives the window ⇄ main transition. Split out of
 * [MediaViewerFullscreenWindow] per the §2 file-size mandate.
 */
@Composable
fun MediaViewerFullscreenSurface(
    modifier: Modifier,
    videoState: VideoPlayerState?,
    subtitleBottomPadding: Dp,
    onSizeModeChange: (SizeMode) -> Unit,
    onZoomHud: () -> Unit
) {
    Box(modifier) {
        // The fullscreen window never shows a still, and the player owns the
        // picture — the 1x1 poster only exists for the frames before the first
        // decode lands.
        val poster = remember { ImageBitmap(1, 1) }
        val media = remember(videoState, poster) {
            if (videoState != null) ViewerMedia.Video(videoState, poster) else ViewerMedia.Still(poster)
        }
        MediaViewerContent(
            media = media,
            animationDirection = 0,
            transform = ViewerTransform(
                PipManager.sizeMode,
                PipManager.offsetX,
                PipManager.offsetY
            ),
            // The fullscreen window has no context menu of its own.
            actions = NO_VIEWER_ACTIONS,
            bottomPadding = subtitleBottomPadding,
            onSizeModeChange = onSizeModeChange,
            onOffsetChange = { x, y ->
                PipManager.offsetX = x
                PipManager.offsetY = y
            },
            onEffectiveScaleChange = { PipManager.scale = it },
            onMetricsChange = { container, native ->
                PipManager.containerSize = container
                PipManager.nativeSize = native
            },
            onZoomHud = onZoomHud,
            onPlayPause = { },
            onToggleFullscreen = { PipManager.closePip(returnToMain = true) }
        )
    }
}
