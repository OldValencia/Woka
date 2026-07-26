package io.iris.ui.viewer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import io.iris.data.CarouselVisibility
import io.iris.data.MediaItem
import io.iris.data.SettingsRepository
import io.iris.ui.viewer.pip.PipManager
import io.iris.ui.viewer.player.VideoPlayerState
import kotlinx.coroutines.delay
import java.awt.MouseInfo
import java.awt.Point

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
        title = item.file.name
    ) {
        var appear by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { appear = true }

        val isVisible = true // In PIP, controls could be auto-hidden, but we can simplify for now

        AnimatedVisibility(
            visible = appear,
            enter = fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.95f),
            exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.95f)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                
                var dragStartMouse by remember { mutableStateOf<Point?>(null) }
                var dragStartWin by remember { mutableStateOf<Point?>(null) }
                
                Box(Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val isCtrl = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
                                
                                if (event.type == PointerEventType.Press && !isCtrl) {
                                    dragStartMouse = MouseInfo.getPointerInfo().location
                                    dragStartWin = window.location
                                }
                                
                                if (event.type == PointerEventType.Move && !isCtrl) {
                                    if (windowState.placement == WindowPlacement.Floating) {
                                        val startMouse = dragStartMouse
                                        val startWin = dragStartWin
                                        if (startMouse != null && startWin != null && event.changes.any { it.pressed }) {
                                            val currentMouse = MouseInfo.getPointerInfo().location
                                            window.location = Point(
                                                startWin.x + (currentMouse.x - startMouse.x),
                                                startWin.y + (currentMouse.y - startMouse.y)
                                            )
                                        }
                                    }
                                }
                                
                                if (event.type == PointerEventType.Release) {
                                    dragStartMouse = null
                                    dragStartWin = null
                                }
                            }
                        }
                    }
                ) {
                    val dummyBitmap = remember { ImageBitmap(1, 1) }
                    // Media rendering
                    // We use dummy thumbnail (null) for video since videoState has currentFrame
                        MediaViewerContent(
                            imageBitmap = dummyBitmap, // Dummy, videoState provides frame
                            animationDirection = 0,
                            scale = PipManager.scale,
                            offsetX = PipManager.offsetX,
                            offsetY = PipManager.offsetY,
                            videoState = videoState,
                            onScaleChange = { PipManager.scale = it },
                            onOffsetChange = { x, y -> PipManager.offsetX = x; PipManager.offsetY = y },
                            onPlayPause = { },
                            onEdit = { },
                            onCopy = { },
                            onInfo = { },
                            onToggleFullscreen = { PipManager.closePip(returnToMain = true) }
                        )
                }

                // Corner flash animation
                val infiniteTransition = rememberInfiniteTransition()
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                
                var showCorners by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(3000)
                    showCorners = false
                }
                
                if (showCorners) {
                    Canvas(Modifier.fillMaxSize()) {
                        val strokeWidth = 3.dp.toPx()
                        val length = 32.dp.toPx()
                        val padding = 16.dp.toPx()
                        val w = size.width
                        val h = size.height
                        val color = Color.White.copy(alpha = alpha)
                        
                        // Top-Left: _ |
                        drawLine(color, start = Offset(padding, padding), end = Offset(padding + length, padding), strokeWidth = strokeWidth)
                        drawLine(color, start = Offset(padding, padding), end = Offset(padding, padding + length), strokeWidth = strokeWidth)
                        
                        // Top-Right
                        drawLine(color, start = Offset(w - padding, padding), end = Offset(w - padding - length, padding), strokeWidth = strokeWidth)
                        drawLine(color, start = Offset(w - padding, padding), end = Offset(w - padding, padding + length), strokeWidth = strokeWidth)
                        
                        // Bottom-Left
                        drawLine(color, start = Offset(padding, h - padding), end = Offset(padding + length, h - padding), strokeWidth = strokeWidth)
                        drawLine(color, start = Offset(padding, h - padding), end = Offset(padding, h - padding - length), strokeWidth = strokeWidth)
                        
                        // Bottom-Right
                        drawLine(color, start = Offset(w - padding, h - padding), end = Offset(w - padding - length, h - padding), strokeWidth = strokeWidth)
                        drawLine(color, start = Offset(w - padding, h - padding), end = Offset(w - padding, h - padding - length), strokeWidth = strokeWidth)
                    }
                }

                // Controls
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
                            onOffsetReset = { PipManager.offsetX = 0f; PipManager.offsetY = 0f; PipManager.scale = 1f },
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
