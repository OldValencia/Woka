package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import com.woka.core.data.MediaItem
import com.woka.feature.viewer.video.PlayerSettingsOverlay
import com.woka.feature.viewer.video.SubtitleVerticalOverlay
import com.woka.feature.viewer.video.VolumeVerticalOverlay
import com.woka.feature.viewer.video.engine.decode.selectAudioTrack

/** How long a volume change keeps the volume popover on screen by itself. */
private const val VOLUME_FLASH_MS = 2000L

/**
 * Everything drawn *over* the media: the transient HUDs, the three vertical
 * popovers and the info dialog.
 *
 * Split out of [MediaViewerPlayer] when the frame strip arrived — that file was
 * at the size ceiling before the strip added a line, and this is the half that
 * has nothing to do with laying the player out.
 */
@Composable
internal fun MediaViewerPlayerOverlays(
    state: MediaViewerState,
    media: ViewerMedia?,
    activeItem: MediaItem?,
    mediaVolume: Float,
    bottomPadding: Dp,
    showInfoDialog: Boolean,
    onShowInfoDialogChange: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    // Read unconditionally: inside the `||` below it would be a conditional
    // composable call, which Compose cannot key.
    val volumeFlash = rememberEventFlag(state.volumeEvent, VOLUME_FLASH_MS)
    val video = media as? ViewerMedia.Video
    val alignment = if (state.isUiVisible) Alignment.BottomEnd else Alignment.CenterEnd

    PlayPauseOverlay(event = state.playPauseEvent, isPlaying = video?.player?.isPlaying == true)
    SkipOverlay(event = state.skipEvent, direction = state.skipDirection)
    ZoomHudOverlay(
        event = state.zoomHudEvent,
        label = "${state.sizeMode.percentIn(state.containerSize, state.nativeSize)}%"
    )
    VolumeVerticalOverlay(
        isVisible = state.hover.visibleTarget == HoverTarget.Volume || volumeFlash,
        volume = mediaVolume,
        bottomPadding = bottomPadding,
        alignment = alignment,
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
            bottomPadding = bottomPadding,
            alignment = alignment,
            player = video.player,
            onHover = { state.hover.setHovered(HoverTarget.Subtitle, it) }
        )
        PlayerSettingsOverlay(
            isVisible = state.hover.visibleTarget == HoverTarget.Settings,
            bottomPadding = bottomPadding,
            alignment = alignment,
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
