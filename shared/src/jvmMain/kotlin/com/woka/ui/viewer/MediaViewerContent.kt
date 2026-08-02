package com.woka.ui.viewer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.woka.ui.viewer.player.VideoPlayerState

@Composable
fun MediaViewerContent(
    imageBitmap: ImageBitmap,
    animationDirection: Int,
    sizeMode: SizeMode,
    offsetX: Float,
    offsetY: Float,
    videoState: VideoPlayerState?,
    bottomPadding: Dp = 40.dp,
    onSizeModeChange: (SizeMode) -> Unit,
    onOffsetChange: (Float, Float) -> Unit,
    onEffectiveScaleChange: (Float) -> Unit,
    onScalingDisabledAttempt: () -> Unit,
    onPlayPause: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onInfo: () -> Unit,
    onToggleFullscreen: () -> Unit
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val sourceBitmap = if (videoState != null) videoState.currentFrame ?: imageBitmap else imageBitmap
    val nativeSize = IntSize(sourceBitmap.width, sourceBitmap.height)

    val targetScale = computeGraphicsScale(sizeMode, containerSize, nativeSize)
    val renderScale by animateFloatAsState(targetScale, animationSpec = tween(200))

    LaunchedEffect(targetScale) { onEffectiveScaleChange(targetScale) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .mediaViewerGestures(
                sizeMode = sizeMode,
                offsetX = offsetX,
                offsetY = offsetY,
                containerSize = containerSize,
                nativeSize = nativeSize,
                videoState = videoState,
                onSizeModeChange = onSizeModeChange,
                onOffsetChange = onOffsetChange,
                onScalingDisabledAttempt = onScalingDisabledAttempt,
                onPlayPause = onPlayPause,
                onToggleFullscreen = onToggleFullscreen
            ),
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
            val hdrInfo = if (videoState != null && frameToDraw === videoState.currentFrame) videoState.currentFrameHdrInfo else null

            val painter = if (hdrInfo?.needsToneMap == true) {
                remember(frameToDraw) { HdrImagePainter(frameToDraw) }
            } else {
                remember(frameToDraw) { BitmapPainter(frameToDraw) }
            }

            ContextMenuArea(
                items = {
                    listOf(
                        ContextMenuItem("Edit") { onEdit() },
                        ContextMenuItem("Copy") { onCopy() },
                        ContextMenuItem("Info") { onInfo() }
                    )
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = renderScale,
                            scaleY = renderScale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                ) {
                    Image(
                        painter = painter,
                        contentDescription = "Image/Video Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        if (videoState != null) {
            SubtitleOverlay(cue = videoState.activeCue, bottomPadding = bottomPadding)
            VideoReloadOverlay(isVisible = videoState.isReloadingVideo)
        }
    }
}
