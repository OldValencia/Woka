package com.woka.feature.viewer.shell

import androidx.compose.animation.*
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.woka.core.data.CarouselVisibility
import com.woka.core.data.SettingsRepository
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
    // Read here rather than threaded down: this window already holds the repository,
    // and the level it shows is the player's own field rather than the screen's.
    val settings by settingsRepo.settings.collectAsState()

    if (videoState != null) {
        VolumeVerticalOverlay(
            isVisible = hover.visibleTarget == HoverTarget.Volume,
            volume = videoState.volume,
            bottomPadding = bottomPadding,
            alignment = alignment,
            audioTracks = videoState.tracks.mediaTracks.audio,
            selectedAudioIndex = videoState.tracks.selectedAudioIndex,
            onSelectAudio = { index -> videoState.selectAudioTrack(index) },
            soundBoost = settings.soundBoost,
            onSoundBoostChange = soundBoostSetter(
                settingsRepo,
                { videoState.volume },
                videoState::updateVolume
            ),
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
                onSeekerHoverChange = { hover.setHovered(HoverTarget.Seeker, it) },
                isFullscreen = true,
                onToggleFullscreen = onExitFullscreen,
                soundBoost = settings.soundBoost
            )
        }
    }
}

/** Top-right size/zoom and window-mode controls of the fullscreen window. */
@Composable
fun BoxScope.MediaViewerFullscreenTopControls(
    isUiVisible: Boolean,
    isPipWindow: Boolean,
    onTogglePipWindow: () -> Unit,
    onSizeModeChange: (SizeMode) -> Unit
) {
    AnimatedVisibility(
        visible = isUiVisible,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
    ) {
        // The controls container styles its own pill surface and border;
        // an outer scrim behind it produced a duplicate translucent background.
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
            isPipWindow = isPipWindow,
            onTogglePipWindow = onTogglePipWindow
        )
    }
}
