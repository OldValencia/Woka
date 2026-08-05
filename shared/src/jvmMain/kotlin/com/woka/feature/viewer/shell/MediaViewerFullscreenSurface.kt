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
        val dummyBitmap = remember { ImageBitmap(1, 1) }
        MediaViewerContent(
            imageBitmap = dummyBitmap,
            animationDirection = 0,
            sizeMode = PipManager.sizeMode,
            offsetX = PipManager.offsetX,
            offsetY = PipManager.offsetY,
            videoState = videoState,
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
            onEdit = { },
            onCopy = { },
            onInfo = { },
            onToggleFullscreen = { PipManager.closePip(returnToMain = true) }
        )
    }
}
