package com.woka.feature.viewer.shell

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.core.data.CarouselVisibility
import com.woka.core.data.SettingsRepository
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.pip.PipManager
import com.woka.feature.viewer.video.MediaViewerVideoControls
import com.woka.feature.viewer.video.PlayerSettingsOverlay
import com.woka.feature.viewer.video.SubtitleVerticalOverlay
import com.woka.feature.viewer.video.VolumeVerticalOverlay
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.decode.selectAudioTrack

/**
 * Toolbar popovers (Volume / Subtitles / Settings) and the bottom control bar of
 * the fullscreen window, split out of [MediaViewerFullscreenWindow] to keep that
 * file under the size mandate (§2).
 */
@Composable
fun BoxScope.MediaViewerFullscreenOverlays(
    videoState: VideoPlayerState?,
    hover: PopoverHoverState,
    isUiVisible: Boolean,
    bottomPadding: Dp,
    settingsRepo: SettingsRepository,
    onExitFullscreen: () -> Unit
) {
    val alignment = if (isUiVisible) Alignment.BottomEnd else Alignment.CenterEnd

    if (videoState != null) {
        VolumeVerticalOverlay(
            isVisible = hover.visibleTarget == HoverTarget.Volume,
            volume = videoState.volume,
            bottomPadding = bottomPadding,
            alignment = alignment,
            audioTracks = videoState.tracks.mediaTracks.audio,
            selectedAudioIndex = videoState.tracks.selectedAudioIndex,
            onSelectAudio = { index -> videoState.selectAudioTrack(index) },
            onVolumeChange = { videoState.updateVolume(it); settingsRepo.setMediaVolume(it) },
            onHover = { hover.setHovered(HoverTarget.Volume, it) }
        )
        SubtitleVerticalOverlay(
            isVisible = hover.visibleTarget == HoverTarget.Subtitle,
            bottomPadding = bottomPadding,
            alignment = alignment,
            player = videoState,
            onHover = { hover.setHovered(HoverTarget.Subtitle, it) }
        )
        PlayerSettingsOverlay(
            isVisible = hover.visibleTarget == HoverTarget.Settings,
            bottomPadding = bottomPadding,
            alignment = alignment,
            player = videoState,
            onHover = { hover.setHovered(HoverTarget.Settings, it) }
        )
    }

    AnimatedVisibility(
        visible = isUiVisible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
    ) {
        if (videoState != null) {
            MediaViewerVideoControls(
                videoState = videoState,
                mediaVolume = videoState.volume,
                onVolumeChange = { videoState.updateVolume(it); settingsRepo.setMediaVolume(it) },
                onVolumeHoverChange = { hover.setHovered(HoverTarget.Volume, it) },
                onSubtitlesHoverChange = { hover.setHovered(HoverTarget.Subtitle, it) },
                onSettingsHoverChange = { hover.setHovered(HoverTarget.Settings, it) },
                isFullscreen = true,
                onToggleFullscreen = onExitFullscreen
            )
        }
    }
}

/** Top-right size/zoom controls of the fullscreen window. */
@Composable
fun BoxScope.MediaViewerFullscreenTopControls(
    isUiVisible: Boolean,
    onSizeModeChange: (SizeMode) -> Unit
) {
    AnimatedVisibility(
        visible = isUiVisible,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .background(Theme.viewer.hudScrim, shape = RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            MediaViewerFullscreenControls(
                sizeMode = PipManager.sizeMode,
                containerSize = PipManager.containerSize,
                nativeSize = PipManager.nativeSize,
                onSizeModeChange = onSizeModeChange,
                onOffsetReset = {
                    PipManager.offsetX = 0f
                    PipManager.offsetY = 0f
                },
                carouselMode = CarouselVisibility.HIDDEN,
                onToggleCarousel = { },
                onEdit = { },
                onInfo = { },
                isInfoOpen = false
            )
        }
    }
}
