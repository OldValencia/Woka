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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
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
    onInfo: () -> Unit
) {
    val currentScale by rememberUpdatedState(scale)
    val currentOffsetX by rememberUpdatedState(offsetX)
    val currentOffsetY by rememberUpdatedState(offsetY)
    
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    onScaleChange((currentScale * zoom).coerceIn(0.5f, 5f))
                    onOffsetChange(currentOffsetX + pan.x, currentOffsetY + pan.y)
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.first().scrollDelta.y
                            val zoomFactor = if (delta > 0) 0.9f else 1.1f
                            onScaleChange((currentScale * zoomFactor).coerceIn(0.1f, 10f))
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .pointerInput(videoState) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (currentScale == 1f) {
                            onScaleChange(2f)
                            if (containerSize != IntSize.Zero) {
                                val centerX = containerSize.width / 2f
                                val centerY = containerSize.height / 2f
                                onOffsetChange(centerX - offset.x, centerY - offset.y)
                            } else {
                                onOffsetChange(0f, 0f)
                            }
                        } else {
                            onScaleChange(1f)
                            onOffsetChange(0f, 0f)
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
                    contentScale = ContentScale.Inside,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                )
            }
        }
    }
}
