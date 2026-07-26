package io.iris.ui.viewer

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import io.iris.data.CarouselVisibility
import io.iris.data.MediaItem
import io.iris.data.SettingsRepository
import io.iris.ui.viewer.pip.PipManager
import io.iris.ui.viewer.player.VideoPlayerState

@Composable
fun MediaViewerFullscreenWindow(
    item: MediaItem,
    videoState: VideoPlayerState?,
    settingsRepo: SettingsRepository
) {
    val windowState = rememberWindowState(placement = WindowPlacement.Maximized)

    Window(
        onCloseRequest = { PipManager.closePip() },
        undecorated = true,
        transparent = true,
        state = windowState,
        title = item.file.name,
        // Preview phase so shortcuts fire before any focused button in the
        // window gets the key (and consume it), matching the main window.
        onPreviewKeyEvent = { handlePipKeyEvent(it, videoState) }
    ) {
        var appear by remember { mutableStateOf(false) }
        // Grab OS focus when the window opens; it is created while the main
        // window is active, so without this it never receives key events
        // (Space, arrows, Escape).
        LaunchedEffect(Unit) {
            appear = true
            window.toFront()
            window.requestFocus()
        }

        val isVisible = true

        AnimatedVisibility(
            visible = appear,
            enter = fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.95f),
            exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.95f)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {

                Box(
                    Modifier.fillMaxSize()
                        .pipWindowDraggable(window)
                ) {
                    val dummyBitmap = remember { ImageBitmap(1, 1) }
                    MediaViewerContent(
                        imageBitmap = dummyBitmap,
                        animationDirection = 0,
                        scale = PipManager.scale,
                        offsetX = PipManager.offsetX,
                        offsetY = PipManager.offsetY,
                        videoState = videoState,
                        onScaleChange = { PipManager.scale = it },
                        onOffsetChange = { x, y ->
                            PipManager.offsetX = x
                            PipManager.offsetY = y
                        },
                        onPlayPause = { },
                        onEdit = { },
                        onCopy = { },
                        onInfo = { },
                        onToggleFullscreen = { PipManager.closePip(returnToMain = true) }
                    )
                }

                PipCornerFlash()

                var showVolumeSlider by remember { mutableStateOf(false) }

                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    if (videoState != null) {
                        MediaViewerVideoControls(
                            videoState = videoState,
                            mediaVolume = videoState.volume,
                            onVolumeChange = { videoState.updateVolume(it) },
                            onVolumeHoverChange = { showVolumeSlider = it },
                            isFullscreen = true,
                            onToggleFullscreen = { PipManager.closePip(returnToMain = true) }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
                            .padding(4.dp)
                    ) {
                        MediaViewerFullscreenControls(
                            scale = PipManager.scale,
                            onScaleChange = { PipManager.scale = it },
                            onOffsetReset = {
                                PipManager.offsetX = 0f
                                PipManager.offsetY = 0f
                                PipManager.scale = 1f
                            },
                            carouselMode = CarouselVisibility.HIDDEN,
                            onToggleCarousel = { },
                            onEdit = { },
                            onInfo = { },
                            isInfoOpen = false
                        )
                    }
                }
            }
        }
    }
}
