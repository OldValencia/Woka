package com.woka.feature.viewer.video

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.shell.SimpleTooltip
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.togglePlayPause
import org.jetbrains.compose.resources.painterResource

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
    /** Passed straight through to the speaker's wheel — see `MediaViewerVolumeControl`. */
    soundBoost: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Theme.colors.surfaceElevated.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val isPlaying = videoState?.isPlaying == true
        
        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()
        val isPressed by interactionSource.collectIsPressedAsState()
        
        val iconAlpha by animateFloatAsState(
            when {
                isPressed -> 0.5f
                isHovered -> 0.8f
                else -> 1f
            }
        )
        
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { videoState?.togglePlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(if (isPlaying) Icons.Static.Pause else Icons.Static.PlayFill),
                contentDescription = "Play/Pause",
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(Theme.colors.textPrimary.copy(alpha = iconAlpha))
            )
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
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
            SimpleTooltip(if (isFullscreen) "Exit Fullscreen" else "Fullscreen") {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onToggleFullscreen() },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(if (isFullscreen) Icons.Static.Collapse else Icons.Static.Expand),
                        contentDescription = "Fullscreen",
                        modifier = Modifier.size(16.dp),
                        colorFilter = ColorFilter.tint(Theme.colors.textPrimary.copy(alpha = iconAlpha))
                    )
                }
            }
        }
    }
}
