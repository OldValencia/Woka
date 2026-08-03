package com.woka.ui.viewer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
    onMetricsChange: (IntSize, IntSize) -> Unit,
    onZoomHud: () -> Unit,
    onPlayPause: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onInfo: () -> Unit,
    onToggleFullscreen: () -> Unit
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val sourceBitmap = if (videoState != null) videoState.currentFrame ?: imageBitmap else imageBitmap
    // The stream's own dimensions, not the decoded frame's: video may be decoded
    // smaller than native to fit the surface (§6), and the zoom presets — Actual
    // size above all — still mean native 1:1.
    val videoTrack = videoState?.tracks?.mediaTracks?.video
        ?.firstOrNull { it.index == videoState.tracks.selectedVideoIndex }
    val nativeSize = if (videoTrack?.width != null && videoTrack.height != null) {
        IntSize(videoTrack.width, videoTrack.height)
    } else {
        IntSize(sourceBitmap.width, sourceBitmap.height)
    }

    val targetScale = computeGraphicsScale(sizeMode, containerSize, nativeSize)
    val renderScale by animateFloatAsState(targetScale, animationSpec = tween(200))

    // While a drag is live the frame must track the pointer exactly; the spring is
    // only for what happens when it is let go — the rubber band settling back to
    // the edge, or the frame re-centring once it no longer overflows.
    var isPanning by remember { mutableStateOf(false) }
    val offsetSpec: AnimationSpec<Float> =
        if (isPanning) snap() else spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
    val drawnOffsetX by animateFloatAsState(offsetX, animationSpec = offsetSpec)
    val drawnOffsetY by animateFloatAsState(offsetY, animationSpec = offsetSpec)

    LaunchedEffect(targetScale) { onEffectiveScaleChange(targetScale) }
    LaunchedEffect(containerSize, nativeSize) { onMetricsChange(containerSize, nativeSize) }
    LaunchedEffect(containerSize, videoState) {
        videoState?.renderScale?.setSurface(containerSize.width, containerSize.height)
    }

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
                onPanActiveChange = { isPanning = it },
                onZoomHud = onZoomHud,
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
            MediaViewerFrame(
                targetBitmap = targetBitmap,
                videoState = videoState,
                renderScale = renderScale,
                offsetX = drawnOffsetX,
                offsetY = drawnOffsetY,
                onEdit = onEdit,
                onCopy = onCopy,
                onInfo = onInfo
            )
        }
        if (videoState != null) {
            SubtitleOverlay(cue = videoState.activeCue, bottomPadding = bottomPadding)
            VideoReloadOverlay(isVisible = videoState.isReloadingVideo)
        }
    }
}
