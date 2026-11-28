package com.woka.feature.viewer.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.woka.core.data.MediaItem
import com.woka.core.ui.components.OriginalLabel
import com.woka.core.ui.components.frames.FrameTransport
import com.woka.feature.viewer.video.FrameSpeedPopover
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
    onVolumeChange: (Float) -> Unit,
    /** Sound boost, as stored — the volume popover both reads it and sets it. */
    soundBoost: Boolean = false,
    onSoundBoostChange: (Boolean) -> Unit = {},
    /**
     * Whether the still on screen is running, or **null** when it is not an animation
     * at all. Null is what tells the play/pause flash to stay down: a photograph
     * receives the same tap and the same Space as everything else, and answering it
     * with a pause glyph is the HUD claiming something stopped that never moved.
     */
    framePlaying: Boolean? = null,
    /** An animation's clock, for its speed popover (RD-501). */
    frameTransport: FrameTransport? = null
) {
    // Read unconditionally: inside the `||` below it would be a conditional
    // composable call, which Compose cannot key.
    val volumeFlash = rememberEventFlag(state.volumeEvent, VOLUME_FLASH_MS)
    val video = media as? ViewerMedia.Video
    val alignment = if (state.isUiVisible) Alignment.BottomEnd else Alignment.CenterEnd

    PlayPauseOverlay(
        event = state.playPauseEvent,
        isPlaying = video?.player?.isPlaying == true || framePlaying == true,
        enabled = video != null || framePlaying != null
    )
    SkipOverlay(event = state.skipEvent, direction = state.skipDirection)
    ZoomHudOverlay(
        event = state.zoomHudEvent,
        label = "${state.sizeMode.percentIn(state.containerSize, state.nativeSize)}%"
    )
    // Over the picture while `Original` shows (RD-502, `.original-label`).
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        OriginalLabel(state.original.active)
    }
    VolumeVerticalOverlay(
        isVisible = state.hover.visibleTarget == HoverTarget.Volume || volumeFlash,
        volume = mediaVolume,
        bottomPadding = bottomPadding,
        alignment = alignment,
        audioTracks = video?.player?.tracks?.mediaTracks?.audio ?: emptyList(),
        selectedAudioIndex = video?.player?.tracks?.selectedAudioIndex,
        onSelectAudio = { index -> video?.player?.selectAudioTrack(index) },
        soundBoost = soundBoost,
        onSoundBoostChange = onSoundBoostChange,
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
    }
    frameTransport?.let { transport ->
        FrameSpeedPopover(
            isVisible = state.hover.visibleTarget == HoverTarget.Speed,
            transport = transport,
            bottomPadding = bottomPadding,
            alignment = alignment,
            onHover = { state.hover.setHovered(HoverTarget.Speed, it) }
        )
    }
    // Any player, a track's included (AP-003): its ⚙ is where Repeat and Shuffle live.
    media?.player?.let { player ->
        PlayerSettingsOverlay(
            isVisible = state.hover.visibleTarget == HoverTarget.Settings,
            bottomPadding = bottomPadding,
            alignment = alignment,
            player = player,
            onHover = { state.hover.setHovered(HoverTarget.Settings, it) }
        )
    }
    if (showInfoDialog && activeItem != null) {
        FileInfoDialog(
            activeItem,
            onDismiss = { onShowInfoDialogChange(false) },
            // `media?.player`, not `video`: a track has its streams too (AP-003).
            mediaTracks = media?.player?.tracks?.mediaTracks,
            durationMs = media?.player?.durationMs,
            selectedAudioIndex = media?.player?.tracks?.selectedAudioIndex
        )
    }
}
