package com.woka.feature.viewer.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.feature.viewer.shell.SimpleTooltip
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.togglePlayPause

/**
 * The transport for a film and a track (RD-501, `#viewer/video`, `#viewer/track`): the round
 * knob, the time and the seek on the left, the per-kind slots on the right. An animation's
 * bar is `FrameTransportBar`, the same box with its own slots.
 */
@Composable
fun MediaViewerVideoControls(
    videoState: VideoPlayerState?,
    mediaVolume: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeHoverChange: (Boolean) -> Unit,
    onSubtitlesHoverChange: (Boolean) -> Unit = {},
    onSettingsHoverChange: (Boolean) -> Unit = {},
    onSeekerHoverChange: (Boolean) -> Unit = {},
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    /** False for a track: there is no picture to take fullscreen (AP-003). */
    canFullscreen: Boolean = true,
    /** Passed straight through to the speaker's wheel — see `MediaViewerVolumeControl`. */
    soundBoost: Boolean = false
) {
    val colors = viewerTransport()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp)
            .transportBar(colors),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TransportTokens.gap)
    ) {
        val isPlaying = videoState?.isPlaying == true
        SimpleTooltip(if (isPlaying) "Pause   ·   Space" else "Play   ·   Space") {
            PlayKnob(isPlaying, colors.knob, "viewer-transport-play") { videoState?.togglePlayPause() }
        }

        // The play head is handed over as a lambda, never read here: `durationMs`
        // changes once per file, `currentPositionMs` changes at the presenter's
        // rate and used to recompose this entire row with it (§10.5).
        val durationMs = videoState?.durationMs ?: 0L

        if (durationMs > 0L) {
            MediaViewerTimeSlider(
                videoState = videoState,
                position = { videoState?.currentPositionMs ?: 0L },
                durationMs = durationMs,
                onHoverChange = onSeekerHoverChange,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(TransportTokens.gap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hidden entirely when the file has no subtitle tracks (§2.2); the time
            // slider (weight 1f) reclaims the freed space, no empty gap left behind.
            if (videoState != null && videoState.tracks.hasAnySubtitle) {
                SubtitleControl(
                    videoState = videoState,
                    onHoverChange = onSubtitlesHoverChange
                )
            }
            MediaViewerVolumeControl(
                videoState = videoState,
                mediaVolume = mediaVolume,
                onVolumeChange = onVolumeChange,
                onVolumeHoverChange = onVolumeHoverChange,
                soundBoost = soundBoost
            )
            PlayerSettingsControl(onHoverChange = onSettingsHoverChange)
            if (canFullscreen) MediaViewerFullscreenButton(isFullscreen, onToggleFullscreen)
        }
    }
}
