package com.woka.feature.viewer.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.core.data.CarouselVisibility
import com.woka.core.data.MediaItem
import com.woka.feature.viewer.video.engine.decode.selectAudioTrack
import com.woka.feature.viewer.video.VolumeVerticalOverlay
import com.woka.feature.viewer.video.PlayerSettingsOverlay
import com.woka.feature.viewer.video.SubtitleVerticalOverlay

/** How long a volume change keeps the volume popover on screen by itself. */
private const val VOLUME_FLASH_MS = 2000L

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
    thumbnails: ViewerThumbnails
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

        if (media != null) {
            MediaViewerContent(
                media = media,
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
                onToggleFullscreen = onToggleFullscreen
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
                onToggleFullscreen = onToggleFullscreen
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // Read unconditionally: inside the `||` below it would be a conditional
        // composable call, which Compose cannot key.
        val volumeFlash = rememberEventFlag(state.volumeEvent, VOLUME_FLASH_MS)
        val video = media as? ViewerMedia.Video

        PlayPauseOverlay(event = state.playPauseEvent, isPlaying = video?.player?.isPlaying == true)
        SkipOverlay(event = state.skipEvent, direction = state.skipDirection)
        ZoomHudOverlay(
            event = state.zoomHudEvent,
            label = "${state.sizeMode.percentIn(state.containerSize, state.nativeSize)}%"
        )
        VolumeVerticalOverlay(
            isVisible = state.hover.visibleTarget == HoverTarget.Volume || volumeFlash,
            volume = mediaVolume,
            bottomPadding = bottomOverlayPadding,
            alignment = if (state.isUiVisible) Alignment.BottomEnd else Alignment.CenterEnd,
            audioTracks = video?.player?.tracks?.mediaTracks?.audio ?: emptyList(),
            selectedAudioIndex = video?.player?.tracks?.selectedAudioIndex,
            onSelectAudio = { index -> video?.player?.selectAudioTrack(index) },
            onVolumeChange = onVolumeChange,
            onHover = { state.hover.setHovered(HoverTarget.Volume, it) }
        )
        // Rendered on the type match rather than early-returning inside: a still
        // has no subtitles and no player settings, and now says so in the types.
        if (video != null) {
            SubtitleVerticalOverlay(
                isVisible = state.hover.visibleTarget == HoverTarget.Subtitle,
                bottomPadding = bottomOverlayPadding,
                alignment = if (state.isUiVisible) Alignment.BottomEnd else Alignment.CenterEnd,
                player = video.player,
                onHover = { state.hover.setHovered(HoverTarget.Subtitle, it) }
            )
            PlayerSettingsOverlay(
                isVisible = state.hover.visibleTarget == HoverTarget.Settings,
                bottomPadding = bottomOverlayPadding,
                alignment = if (state.isUiVisible) Alignment.BottomEnd else Alignment.CenterEnd,
                player = video.player,
                onHover = { state.hover.setHovered(HoverTarget.Settings, it) }
            )
        }
        if (showInfoDialog && activeItem != null) {
            FileInfoDialog(
                activeItem,
                onDismiss = { onShowInfoDialogChange(false) },
                mediaTracks = video?.player?.tracks?.mediaTracks,
                durationMs = video?.player?.durationMs,
                selectedAudioIndex = video?.player?.tracks?.selectedAudioIndex
            )
        }
    }
}
