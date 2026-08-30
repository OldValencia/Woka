package com.woka.feature.viewer.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.core.data.CarouselVisibility
import com.woka.core.data.MediaItem
import com.woka.core.ui.components.WokaSpinner
import com.woka.core.ui.theme.Theme
import com.woka.core.ui.components.frames.FRAME_STRIP_WIDTH
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
    /** Sound boost, as stored — read by the volume popover and the speaker's wheel. */
    soundBoost: Boolean = false,
    onSoundBoostChange: (Boolean) -> Unit = {},
    onSizeModeChange: (SizeMode) -> Unit,
    onToggleFullscreen: () -> Unit,
    thumbnails: ViewerThumbnails,
    strip: FrameStripModel = FrameStripModel(),
    /** Something is drawn over this viewer, so its animation must stand still. */
    isFrozen: Boolean = false
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

        // An animation's frames are not new pictures arriving, so the crossfade is keyed
        // on the file rather than on the poster it happens to be showing right now.
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
                ambientFill = chrome.isFullscreen && chrome.ambientFill
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
                soundBoost = soundBoost,
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

        MediaViewerPlayerFrames(
            state = state,
            strip = strip,
            activeItem = activeItem,
            thumbnails = thumbnails,
            showStrip = showStrip,
            bottomPadding = bottomOverlayPadding,
            isFrozen = isFrozen
        )

        MediaViewerPlayerOverlays(
            state = state,
            media = media,
            activeItem = activeItem,
            mediaVolume = mediaVolume,
            bottomPadding = bottomOverlayPadding,
            showInfoDialog = showInfoDialog,
            onShowInfoDialogChange = onShowInfoDialogChange,
            onVolumeChange = onVolumeChange,
            soundBoost = soundBoost,
            onSoundBoostChange = onSoundBoostChange,
            framePlaying = if (strip.isAnimated) state.isFramePlaying else null
        )
    }
}
