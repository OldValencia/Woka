package io.iris.ui.viewer

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import io.iris.data.MediaItem
import io.iris.ui.theme.IrisTheme
import io.iris.ui.viewer.player.VideoPlayerState

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MediaViewerToolbar(
    currentId: String,
    mediaItems: List<MediaItem>,
    imageBitmap: ImageBitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectMedia: (String) -> Unit,
    onVolumeHoverChange: (Boolean) -> Unit,
    isVisible: Boolean,
    isCarouselEnabled: Boolean,
    loadThumbnail: suspend (MediaItem) -> ImageBitmap?,
    mediaVolume: Float,
    onVolumeChange: (Float) -> Unit,
    videoState: VideoPlayerState?
) {
    val currentItem = mediaItems.find { it.file.absolutePath == currentId }
    val currentIndex = mediaItems.indexOfFirst { it.file.absolutePath == currentId }
    val hasPrev = currentIndex > 0
    val hasNext = currentIndex != -1 && currentIndex < mediaItems.lastIndex
    
    val animationProgress by animateFloatAsState(
        targetValue = if (isVisible) 0f else 1f,
        animationSpec = tween(300, easing = LinearOutSlowInEasing)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .mediaViewerBackgroundClip(
                animationProgress = animationProgress,
                hasPrev = hasPrev,
                hasNext = hasNext,
                hasCarousel = isCarouselEnabled && mediaItems.size > 1,
                isVideo = currentItem is MediaItem.Video
            )
            .background(IrisTheme.colors.background.copy(alpha = 0.35f)),
        contentAlignment = Alignment.TopCenter
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.4f) }),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale * 1.05f,
                    scaleY = scale * 1.05f,
                    translationX = offsetX,
                    translationY = offsetY,
                    renderEffect = BlurEffect(50f / scale, 50f / scale)
                )
        )
        Box(modifier = Modifier.fillMaxSize().background(IrisTheme.colors.background.copy(alpha = 0.4f)))


        if (hasPrev) {
            MediaViewerPrevButton(
                animationProgress = animationProgress,
                onPrev = onPrev,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }

        if (hasNext) {
            MediaViewerNextButton(
                animationProgress = animationProgress,
                onNext = onNext,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
        
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 200.dp * animationProgress)
                .alpha(1f - animationProgress)
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            if (currentItem is MediaItem.Video) {
                MediaViewerVideoControls(
                    videoState = videoState,
                    mediaVolume = mediaVolume,
                    onVolumeChange = onVolumeChange,
                    onVolumeHoverChange = onVolumeHoverChange
                )
            }

            if (isCarouselEnabled) {
                MediaViewerCarousel(
                    mediaItems = mediaItems,
                    currentId = currentId,
                    currentIndex = currentIndex,
                    onSelectMedia = onSelectMedia,
                    loadThumbnail = loadThumbnail
                )
            }
        }
    }
}
