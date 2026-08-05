package com.woka.feature.viewer.shell

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import com.woka.core.data.MediaItem
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.video.MediaViewerVideoControls

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MediaViewerToolbar(
    currentId: String,
    mediaItems: List<MediaItem>,
    media: ViewerMedia,
    backdrop: BackdropTransform,
    chrome: ViewerChromeState,
    hover: PopoverHoverState,
    isVisible: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectMedia: (String) -> Unit,
    loadThumbnail: suspend (MediaItem) -> ImageBitmap?,
    mediaVolume: Float,
    onVolumeChange: (Float) -> Unit,
    onToggleFullscreen: () -> Unit
) {
    // Two linear scans over the playlist; unremembered they ran on every
    // recomposition, which — before the backdrop moved out below — meant on every
    // presented video frame (§10.4).
    val currentItem = remember(currentId, mediaItems) {
        mediaItems.find { it.file.absolutePath == currentId }
    }
    val currentIndex = remember(currentId, mediaItems) {
        mediaItems.indexOfFirst { it.file.absolutePath == currentId }
    }
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
                hasCarousel = chrome.isCarouselEnabled && mediaItems.size > 1 && !chrome.isFullscreen,
                isVideo = currentItem is MediaItem.Video
            )
            .background(Theme.colors.background.copy(alpha = 0.35f)),
        contentAlignment = Alignment.TopCenter
    ) {
        ViewerBackdrop(media = media, backdrop = backdrop)
        Box(modifier = Modifier.fillMaxSize().background(Theme.colors.background.copy(alpha = 0.4f)))


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
                    videoState = media.player,
                    mediaVolume = mediaVolume,
                    onVolumeChange = onVolumeChange,
                    onVolumeHoverChange = { hover.setHovered(HoverTarget.Volume, it) },
                    onSubtitlesHoverChange = { hover.setHovered(HoverTarget.Subtitle, it) },
                    onSettingsHoverChange = { hover.setHovered(HoverTarget.Settings, it) },
                    isFullscreen = chrome.isFullscreen,
                    onToggleFullscreen = onToggleFullscreen
                )
            }

            if (chrome.isCarouselEnabled && !chrome.isFullscreen) {
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
