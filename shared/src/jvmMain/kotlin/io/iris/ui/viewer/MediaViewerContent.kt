package io.iris.ui.viewer

import androidx.compose.animation.*
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import io.iris.ui.viewer.player.VideoPlayerState

@Composable
fun MediaViewerContent(
    imageBitmap: ImageBitmap,
    animationDirection: Int,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    videoState: VideoPlayerState?,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Float, Float) -> Unit,
    onPlayPause: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onInfo: () -> Unit,
    onToggleFullscreen: () -> Unit
) {
    val currentScale by rememberUpdatedState(scale)
    val currentOffsetX by rememberUpdatedState(offsetX)
    val currentOffsetY by rememberUpdatedState(offsetY)
    
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    var dragWithCtrl by remember { mutableStateOf(false) }

    var renderScale by remember { mutableStateOf(scale) }
    var renderOffsetX by remember { mutableStateOf(offsetX) }
    var renderOffsetY by remember { mutableStateOf(offsetY) }
    var isUserDragging by remember { mutableStateOf(false) }

    LaunchedEffect(scale) { if (!isUserDragging) renderScale = scale }
    LaunchedEffect(offsetX, offsetY) {
        if (!isUserDragging) {
            renderOffsetX = offsetX
            renderOffsetY = offsetY
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press) {
                            val isCtrl = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
                            dragWithCtrl = isCtrl
                        }
                        
                        if (event.type == PointerEventType.Scroll) {
                            val isCtrl = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
                            if (isCtrl) {
                                val delta = event.changes.first().scrollDelta.y
                                val zoomFactor = if (delta > 0) 0.9f else 1.1f
                                renderScale = (renderScale * zoomFactor).coerceIn(0.1f, 10f)
                                onScaleChange(renderScale)
                                event.changes.forEach { it.consume() }
                            }
                        }

                        if (event.type == PointerEventType.Release) {
                            dragWithCtrl = false
                            isUserDragging = false
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                var initialized = false
                
                detectTransformGestures { _, pan, zoom, _ ->
                    if (dragWithCtrl) {
                        isUserDragging = true
                        if (!initialized) {
                            renderScale = currentScale
                            renderOffsetX = currentOffsetX
                            renderOffsetY = currentOffsetY
                            initialized = true
                        }
                        
                        renderScale = (renderScale * zoom).coerceIn(0.1f, 10f)
                        renderOffsetX += pan.x
                        renderOffsetY += pan.y
                        
                        onScaleChange(renderScale)
                        onOffsetChange(renderOffsetX, renderOffsetY)
                    } else {
                        initialized = false
                    }
                }
            }
            .pointerInput(videoState) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (videoState != null) {
                            onToggleFullscreen()
                        } else {
                            if (currentScale == 1f) {
                                renderScale = 2f
                                if (containerSize != IntSize.Zero) {
                                    val centerX = containerSize.width / 2f
                                    val centerY = containerSize.height / 2f
                                    renderOffsetX = centerX - offset.x
                                    renderOffsetY = centerY - offset.y
                                } else {
                                    renderOffsetX = 0f
                                    renderOffsetY = 0f
                                }
                            } else {
                                renderScale = 1f
                                renderOffsetX = 0f
                                renderOffsetY = 0f
                            }
                            onScaleChange(renderScale)
                            onOffsetChange(renderOffsetX, renderOffsetY)
                        }
                    },
                    onTap = {
                        if (videoState != null) {
                            videoState.togglePlayPause()
                            onPlayPause()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = imageBitmap,
            transitionSpec = {
                if (animationDirection == 0) {
                    fadeIn() togetherWith fadeOut()
                } else {
                    val sign = animationDirection
                    slideInHorizontally { it * sign } togetherWith slideOutHorizontally { -it * sign }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { targetBitmap ->
            val frameToDraw = if (videoState != null) videoState.currentFrame ?: targetBitmap else targetBitmap
            ContextMenuArea(
                items = {
                    listOf(
                        ContextMenuItem("Edit") { onEdit() },
                        ContextMenuItem("Copy") { onCopy() },
                        ContextMenuItem("Info") { onInfo() }
                    )
                }
            ) {
                Image(
                    bitmap = frameToDraw,
                    contentDescription = "Image/Video Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = renderScale,
                            scaleY = renderScale,
                            translationX = renderOffsetX,
                            translationY = renderOffsetY
                        )
                )
            }
        }
    }
}
