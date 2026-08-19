package com.woka.feature.viewer.shell

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.woka.feature.viewer.shell.ambient.AmbientFillLayer
import com.woka.feature.viewer.video.PlaybackHealthNotice
import com.woka.feature.viewer.video.SubtitleOverlay
import com.woka.feature.viewer.video.VideoReloadOverlay
import com.woka.feature.viewer.video.engine.activeCue
import kotlin.math.min

@Composable
fun MediaViewerContent(
    media: ViewerMedia,
    contentKey: Any,
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
    bottomPadding: Dp = 40.dp,
    panRequiresCtrl: Boolean = false,
    ambientFill: Boolean = false,
    isAnimated: Boolean = false
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
                onToggleFullscreen = onToggleFullscreen,
                panRequiresCtrl = panRequiresCtrl
            ),
        contentAlignment = Alignment.Center
    ) {
        if (ambientFill && media is ViewerMedia.Video &&
            hasSideBars(containerSize, nativeSize, renderScale)
        ) {
            AmbientFillLayer(media.player)
        }
        AnimatedContent(
            targetState = contentKey,
            transitionSpec = {
                if (animationDirection == 0) {
                    if (isAnimated) {
                        // A frame of an animation is not a new picture arriving: crossfading
                        // every 40 ms reads as a flicker (ME-140).
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn() togetherWith fadeOut()
                    }
                } else {
                    val sign = animationDirection
                    slideInHorizontally { it * sign } togetherWith slideOutHorizontally { -it * sign }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { targetKey ->
            var cachedPoster by remember { mutableStateOf(media.poster) }
            var cachedMedia by remember { mutableStateOf(media) }

            if (targetKey == contentKey) {
                cachedPoster = media.poster
                cachedMedia = media
            }

            MediaViewerFrame(
                media = cachedMedia,
                targetPoster = cachedPoster,
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
            // Here rather than in either window's own overlays, so the main window
            // and the fullscreen one get it from one call site.
            PlaybackHealthNotice(videoState = media.player, bottomPadding = bottomPadding)
        }
    }
}

/**
 * Whether the drawn picture leaves the container's *sides* empty — a 16:9 file on a
 * wider panel, which is a monitor mismatch rather than anything the file asked for.
 * The top/bottom letterbox of 2.39:1 content is graded in and is not this.
 *
 * `ContentScale.Fit` sizes the picture, and `renderScale` is the graphics-layer
 * scale applied on top of it, so the drawn width is the fitted one times that.
 */
private fun hasSideBars(container: IntSize, native: IntSize, scale: Float): Boolean {
    if (container.width <= 0 || container.height <= 0) return false
    if (native.width <= 0 || native.height <= 0) return false
    val fit = min(
        container.width.toFloat() / native.width,
        container.height.toFloat() / native.height
    )
    return native.width * fit * scale < container.width - 2f
}
