package com.woka.feature.viewer.shell

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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.woka.feature.viewer.video.SubtitleOverlay
import com.woka.feature.viewer.video.VideoReloadOverlay

@Composable
fun MediaViewerContent(
    media: ViewerMedia,
    animationDirection: Int,
    transform: ViewerTransform,
    actions: ViewerActions,
    onSizeModeChange: (SizeMode) -> Unit,
    onOffsetChange: (Float, Float) -> Unit,
    onEffectiveScaleChange: (Float) -> Unit,
    onMetricsChange: (IntSize, IntSize) -> Unit,
    onZoomHud: () -> Unit,
    onPlayPause: () -> Unit,
    onToggleFullscreen: () -> Unit,
    bottomPadding: Dp = 40.dp
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val nativeSize = media.nativeSize
    val targetScale = computeGraphicsScale(transform.sizeMode, containerSize, nativeSize)
    val renderScale by animateFloatAsState(targetScale, animationSpec = tween(200))

    // While a drag is live the frame must track the pointer exactly; the spring is
    // only for what happens when it is let go — the rubber band settling back to
    // the edge, or the frame re-centring once it no longer overflows.
    var isPanning by remember { mutableStateOf(false) }
    val offsetSpec: AnimationSpec<Float> =
        if (isPanning) snap() else spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
    val drawnOffsetX by animateFloatAsState(transform.offsetX, animationSpec = offsetSpec)
    val drawnOffsetY by animateFloatAsState(transform.offsetY, animationSpec = offsetSpec)

    LaunchedEffect(targetScale) { onEffectiveScaleChange(targetScale) }
    LaunchedEffect(containerSize, nativeSize) { onMetricsChange(containerSize, nativeSize) }
    LaunchedEffect(containerSize, media) {
        media.player?.renderScale?.setSurface(containerSize.width, containerSize.height)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .mediaViewerGestures(
                sizeMode = transform.sizeMode,
                offsetX = transform.offsetX,
                offsetY = transform.offsetY,
                containerSize = containerSize,
                nativeSize = nativeSize,
                videoState = media.player,
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
            targetState = media.poster,
            transitionSpec = {
                if (animationDirection == 0) {
                    fadeIn() togetherWith fadeOut()
                } else {
                    val sign = animationDirection
                    slideInHorizontally { it * sign } togetherWith slideOutHorizontally { -it * sign }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { targetPoster ->
            MediaViewerFrame(
                media = media,
                targetPoster = targetPoster,
                renderScale = renderScale,
                offsetX = drawnOffsetX,
                offsetY = drawnOffsetY,
                subtitleBottomPadding = bottomPadding,
                actions = actions
            )
        }
        if (media is ViewerMedia.Video) {
            SubtitleOverlay(cue = media.player.activeCue, bottomPadding = bottomPadding)
            VideoReloadOverlay(isVisible = media.player.isReloadingVideo)
        }
    }
}
