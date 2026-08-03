package com.woka.ui.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.woka.data.CarouselVisibility
import com.woka.data.MediaItem
import com.woka.ui.viewer.player.VideoPlayerState
import com.woka.ui.viewer.player.selectAudioTrack

@Composable
fun MediaViewerPlayer(
    modifier: Modifier,
    state: MediaViewerState,
    videoState: VideoPlayerState?,
    mediaItems: List<MediaItem>,
    activeItem: MediaItem?,
    mediaVolume: Float,
    carouselMode: CarouselVisibility,
    isFullscreen: Boolean,
    showInfoDialog: Boolean,
    onShowInfoDialogChange: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSizeModeChange: (SizeMode) -> Unit,
    onToggleFullscreen: () -> Unit,
    loadThumbnail: suspend (MediaItem) -> ImageBitmap?,
    editAction: () -> Unit,
    copyAction: () -> Unit,
    infoAction: () -> Unit
) {
    Box(modifier = modifier) {
        val bottomOverlayPadding = if (state.isUiVisible) {
            if (carouselMode != CarouselVisibility.HIDDEN && mediaItems.size > 1 && !isFullscreen) 170.dp else 90.dp
        } else 0.dp
        // Subtitles sit low near the bottom; they only lift enough to clear the
        // control bar (or carousel) while the UI is showing.
        val subtitleBottomPadding = if (state.isUiVisible) {
            if (carouselMode != CarouselVisibility.HIDDEN && mediaItems.size > 1 && !isFullscreen) 180.dp else 100.dp
        } else 40.dp

        if (state.imageBitmap != null) {
            MediaViewerContent(
                imageBitmap = state.imageBitmap!!, animationDirection = state.animationDirection,
                sizeMode = state.sizeMode, offsetX = state.offsetX, offsetY = state.offsetY, videoState = videoState,
                bottomPadding = subtitleBottomPadding,
                onSizeModeChange = onSizeModeChange,
                onOffsetChange = { x, y -> state.offsetX = x; state.offsetY = y },
                onEffectiveScaleChange = { state.scale = it },
                onMetricsChange = { container, native ->
                    state.containerSize = container
                    state.nativeSize = native
                },
                onZoomHud = { state.zoomHudTrigger = System.currentTimeMillis() },
                onPlayPause = { state.playPauseTrigger = System.currentTimeMillis() },
                onEdit = editAction,
                onCopy = copyAction,
                onInfo = infoAction,
                onToggleFullscreen = onToggleFullscreen
            )
            MediaViewerToolbar(
                currentId = state.currentId, mediaItems = mediaItems, imageBitmap = state.imageBitmap!!,
                isVisible = state.isUiVisible || carouselMode == CarouselVisibility.ALWAYS_ON,
                isCarouselEnabled = carouselMode != CarouselVisibility.HIDDEN,
                scale = state.scale, offsetX = state.offsetX, offsetY = state.offsetY,
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
                onVolumeHoverChange = { state.hover.setHovered(HoverTarget.Volume, it) },
                onSubtitlesHoverChange = { state.hover.setHovered(HoverTarget.Subtitle, it) },
                onSettingsHoverChange = { state.hover.setHovered(HoverTarget.Settings, it) },
                loadThumbnail = loadThumbnail,
                mediaVolume = mediaVolume,
                onVolumeChange = onVolumeChange,
                videoState = videoState,
                isFullscreen = isFullscreen,
                onToggleFullscreen = onToggleFullscreen
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        PlayPauseOverlay(trigger = state.playPauseTrigger, isPlaying = videoState?.isPlaying == true)
        SkipOverlay(trigger = state.skipTrigger, direction = state.skipDirection)
        ZoomHudOverlay(
            trigger = state.zoomHudTrigger,
            label = "${state.sizeMode.percentIn(state.containerSize, state.nativeSize)}%"
        )
        VolumeVerticalOverlay(
            isVisible = state.hover.visibleTarget == HoverTarget.Volume || state.volumeTriggerTimer > 0,
            volume = mediaVolume,
            bottomPadding = bottomOverlayPadding,
            alignment = if (state.isUiVisible) Alignment.BottomEnd else Alignment.CenterEnd,
            audioTracks = videoState?.tracks?.mediaTracks?.audio ?: emptyList(),
            selectedAudioIndex = videoState?.tracks?.selectedAudioIndex,
            onSelectAudio = { index -> videoState?.selectAudioTrack(index) },
            onVolumeChange = onVolumeChange,
            onHover = { state.hover.setHovered(HoverTarget.Volume, it) }
        )
        SubtitleVerticalOverlay(
            isVisible = state.hover.visibleTarget == HoverTarget.Subtitle,
            bottomPadding = bottomOverlayPadding,
            alignment = if (state.isUiVisible) Alignment.BottomEnd else Alignment.CenterEnd,
            videoState = videoState,
            onHover = { state.hover.setHovered(HoverTarget.Subtitle, it) }
        )
        PlayerSettingsOverlay(
            isVisible = state.hover.visibleTarget == HoverTarget.Settings,
            bottomPadding = bottomOverlayPadding,
            alignment = if (state.isUiVisible) Alignment.BottomEnd else Alignment.CenterEnd,
            videoState = videoState,
            onHover = { state.hover.setHovered(HoverTarget.Settings, it) }
        )
        if (showInfoDialog && activeItem != null) {
            FileInfoDialog(
                activeItem,
                onDismiss = { onShowInfoDialogChange(false) },
                mediaTracks = videoState?.tracks?.mediaTracks,
                durationMs = videoState?.durationMs,
                selectedAudioIndex = videoState?.tracks?.selectedAudioIndex
            )
        }
    }
}
