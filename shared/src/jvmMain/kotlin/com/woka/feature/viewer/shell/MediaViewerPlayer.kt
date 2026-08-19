package com.woka.feature.viewer.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.core.data.CarouselVisibility
import com.woka.core.data.MediaItem
import com.woka.core.ui.components.WokaSpinner
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.frames.FRAME_STRIP_WIDTH
import com.woka.feature.viewer.shell.frames.FrameStrip
import com.woka.feature.viewer.shell.frames.FramePauseRequestEffect
import com.woka.feature.viewer.shell.frames.FramePlaybackEffect
import com.woka.feature.viewer.shell.frames.FrameStripModel

@Composable
fun MediaViewerPlayer(
    modifier: Modifier,
    state: MediaViewerState,
    media: ViewerMedia?,
    mediaItems: List<MediaItem>,
    activeItem: MediaItem?,
    mediaVolume: Float,
    chrome: ViewerChromeState,
    actions: ViewerActions,
    showInfoDialog: Boolean,
    onShowInfoDialogChange: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSizeModeChange: (SizeMode) -> Unit,
    onToggleFullscreen: () -> Unit,
    thumbnails: ViewerThumbnails,
    strip: FrameStripModel = FrameStripModel()
) {
    Box(modifier = modifier) {
        val hasCarousel = chrome.isCarouselEnabled && mediaItems.size > 1 && !chrome.isFullscreen
        val bottomOverlayPadding = if (state.isUiVisible) {
            if (hasCarousel) 170.dp else 90.dp
        } else 0.dp
        // Subtitles sit low near the bottom; they only lift enough to clear the
        // control bar (or carousel) while the UI is showing.
        val subtitleBottomPadding = if (state.isUiVisible) {
            if (hasCarousel) 180.dp else 100.dp
        } else 40.dp
        // The strip owns the left edge while it is up, so the Prev arrow — which
        // is anchored there too — steps aside rather than sitting under it.
        val showStrip = !strip.isSingle && state.isUiVisible
        val navStartInset = if (showStrip) FRAME_STRIP_WIDTH else 0.dp

        val contentKey = if (strip.isAnimated) state.currentId else media?.poster ?: state.currentId

        if (media != null) {
            MediaViewerContent(
                media = media,
                contentKey = contentKey,
                animationDirection = state.animationDirection,
                transform = ViewerTransform(state.sizeMode, state.offsetX, state.offsetY),
                actions = actions,
                bottomPadding = subtitleBottomPadding,
                onSizeModeChange = onSizeModeChange,
                onOffsetChange = { x, y -> state.offsetX = x; state.offsetY = y },
                onEffectiveScaleChange = { state.scale = it },
                onMetricsChange = { container, native ->
                    state.containerSize = container
                    state.nativeSize = native
                },
                onZoomHud = { state.zoomHudEvent.raise() },
                onPlayPause = { state.playPauseEvent.raise() },
                onToggleFullscreen = onToggleFullscreen,
                ambientFill = chrome.isFullscreen && chrome.ambientFill,
                isAnimated = strip.isAnimated
            )
            MediaViewerToolbar(
                currentId = state.currentId,
                mediaItems = mediaItems,
                media = media,
                isVisible = state.isUiVisible || chrome.carouselMode == CarouselVisibility.ALWAYS_ON,
                chrome = chrome,
                hover = state.hover,
                backdrop = BackdropTransform(state.scale, state.offsetX, state.offsetY),
                onPrev = {
                    val idx = mediaItems.indexOfFirst { it.file.absolutePath == state.currentId }
                    if (idx > 0) {
                        state.animationDirection = -1; state.currentId = mediaItems[idx - 1].file.absolutePath
                    }
                },
                onNext = {
                    val idx = mediaItems.indexOfFirst { it.file.absolutePath == state.currentId }
                    if (idx in 0 until mediaItems.lastIndex) {
                        state.animationDirection = 1; state.currentId = mediaItems[idx + 1].file.absolutePath
                    }
                },
                onSelectMedia = { state.animationDirection = 0; state.currentId = it },
                thumbnails = thumbnails,
                mediaVolume = mediaVolume,
                onVolumeChange = onVolumeChange,
                onToggleFullscreen = onToggleFullscreen,
                navStartInset = navStartInset
            )
        } else {
            // Was Material's indicator, which without a MaterialTheme resolved to
            // the default Material purple. This sits on the media backdrop, so it
            // takes the on-scrim foreground like every other mark drawn there.
            WokaSpinner(
                modifier = Modifier.align(Alignment.Center),
                diameter = 40.dp,
                color = Theme.viewer.onScrim,
                strokeWidth = 3.dp
            )
        }

        // Only for a file that has more than one picture in it, so an ordinary
        // photograph composes byte for byte the tree it always did.
        LaunchedEffect(strip) { state.frameCount = strip.frames.size.coerceAtLeast(1) }
        // The clock, which returns before its first delay for anything that is not an
        // animation — a photograph composes the tree it always did (ME-140).
        FramePlaybackEffect(state, strip)
        FramePauseRequestEffect(state, strip)
        if (activeItem != null && showStrip) {
            FrameStrip(
                item = activeItem,
                frames = strip.frames,
                selectedIndex = state.frameIndex,
                loadPreview = { item, frame -> thumbnails.preview(item, frame) },
                onSelectFrame = {
                    // Reaching for a frame by hand is asking the animation to stand
                    // still; one that walked away from the frame just chosen would be
                    // the only surprising thing here.
                    state.isFramePlaying = false
                    state.frameIndex = it
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp, top = 72.dp, bottom = bottomOverlayPadding)
            )
        }

        MediaViewerPlayerOverlays(
            state = state,
            media = media,
            activeItem = activeItem,
            mediaVolume = mediaVolume,
            bottomPadding = bottomOverlayPadding,
            showInfoDialog = showInfoDialog,
            onShowInfoDialogChange = onShowInfoDialogChange,
            onVolumeChange = onVolumeChange,
            isFramePlaying = strip.isAnimated && state.isFramePlaying
        )
    }
}
