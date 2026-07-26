package io.iris.ui.viewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.iris.ui.icons.IrisIcons
import io.iris.ui.theme.IrisTheme
import io.iris.ui.viewer.player.VideoPlayerState
import org.jetbrains.compose.resources.painterResource

@Composable
fun MediaViewerVideoControls(
    videoState: VideoPlayerState?,
    mediaVolume: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeHoverChange: (Boolean) -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(IrisTheme.colors.surfaceElevated.copy(alpha = 0.5f))
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
            Icon(
                painter = painterResource(if (isPlaying) IrisIcons.Static.Pause else IrisIcons.Static.PlayFill),
                contentDescription = "Play/Pause",
                tint = IrisTheme.colors.textPrimary.copy(alpha = iconAlpha),
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { if (isPlaying) scaleX = 0.7f }
            )
        }
        
        val currentMs = videoState?.currentPositionMs ?: 0L
        val durationMs = videoState?.durationMs ?: 0L
        
        if (durationMs > 0L) {
            MediaViewerTimeSlider(
                videoState = videoState,
                currentMs = currentMs,
                durationMs = durationMs,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        
        Spacer(Modifier.width(4.dp))
        
        MediaViewerVolumeControl(
            videoState = videoState,
            mediaVolume = mediaVolume,
            onVolumeChange = onVolumeChange,
            onVolumeHoverChange = onVolumeHoverChange
        )
        
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onToggleFullscreen() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(if (isFullscreen) IrisIcons.Static.Collapse else IrisIcons.Static.Expand),
                contentDescription = "Fullscreen",
                tint = IrisTheme.colors.textPrimary.copy(alpha = iconAlpha),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
