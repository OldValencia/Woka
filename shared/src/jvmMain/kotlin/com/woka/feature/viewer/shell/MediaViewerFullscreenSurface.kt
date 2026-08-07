package com.woka.feature.viewer.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import com.woka.app.collectField
import com.woka.core.data.SettingsRepository
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
    settingsRepo: SettingsRepository,
    onSizeModeChange: (SizeMode) -> Unit,
    onZoomHud: () -> Unit
) {
    Box(modifier) {
        // The window's own menu: no file actions, one setting. `ContextMenuItem`
        // carries no checkbox, so the state goes in the label.
        val alwaysOnTop by settingsRepo.collectField { it.pipAlwaysOnTop }
        val menuActions = remember(alwaysOnTop) {
            NO_VIEWER_ACTIONS.copy(
                extra = listOf(
                    ViewerMenuEntry(
                        label = if (alwaysOnTop) "✓ Always on Top" else "Always on Top",
                        onClick = { settingsRepo.setPipAlwaysOnTop(!alwaysOnTop) }
                    )
                )
            )
        }
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
            actions = menuActions,
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
            onToggleFullscreen = { PipManager.closePip(returnToMain = true) },
            // A plain left drag belongs to the window here: `pipWindowDraggable`
            // moves the PiP window with it, and panning the picture at the same
            // time is what TC-PIP-02 caught. Ctrl + LMB pans instead.
            panRequiresCtrl = true
        )
    }
}
