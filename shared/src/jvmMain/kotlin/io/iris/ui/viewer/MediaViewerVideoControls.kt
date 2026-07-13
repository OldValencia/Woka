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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.iris.ui.icons.IrisIcons
import io.iris.ui.viewer.player.VideoPlayerState
import org.jetbrains.compose.resources.painterResource

@Composable
fun MediaViewerVideoControls(
    videoState: VideoPlayerState?,
    mediaVolume: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeHoverChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
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

        SimpleTooltip(text = if (isPlaying) "Pause" else "Play") {
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
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = iconAlpha),
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { if (isPlaying) scaleX = 0.7f }
                )
            }
        }

        val currentMs = videoState?.currentPositionMs ?: 0L
        val durationMs = videoState?.durationMs ?: 0L

        Text(
            formatTime(currentMs),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium
        )

        if (durationMs >= 6000L) {
            SimpleTooltip(text = "Skip Backward 5s") {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }) {
                            videoState?.seek(
                                (currentMs - 5000).coerceAtLeast(
                                    0
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(IrisIcons.Static.SkipBackward5),
                        contentDescription = "Skip Backward 5s",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(16.dp).height(18.dp)
                    )
                }
            }
        }

        EchoSlider(
            value = if (durationMs > 0) (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
            onValueChange = {
                videoState?.pauseForSeek()
                videoState?.seek((it * durationMs).toLong())
            },
            onValueChangeFinished = {
                videoState?.resumeAfterSeek()
            },
            tooltipText = { formatTime((it * durationMs).toLong()) },
            modifier = Modifier.weight(1f).height(16.dp)
        )

        if (durationMs >= 6000L) {
            SimpleTooltip(text = "Skip Forward 5s") {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }) {
                            videoState?.seek(
                                (currentMs + 5000).coerceAtMost(
                                    durationMs
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(IrisIcons.Static.SkipForward5),
                        contentDescription = "Skip Forward 5s",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(16.dp).height(18.dp)
                    )
                }
            }
        }

        Text(
            formatTime(durationMs),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium
        )

        Spacer(Modifier.width(8.dp))

        // Repeat Button
        SimpleTooltip("Repeat") {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { videoState?.isRepeatMode = !videoState.isRepeatMode },
                contentAlignment = Alignment.Center
            ) {
                val repeatMode = videoState?.isRepeatMode == true
                Icon(
                    painter = painterResource(if (repeatMode) IrisIcons.Static.Undo else IrisIcons.Static.Redo), // Placeholder until repeat icons are added
                    contentDescription = "Repeat",
                    tint = if (repeatMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        MediaViewerVolumeControl(
            videoState = videoState,
            mediaVolume = mediaVolume,
            onVolumeChange = onVolumeChange,
            onVolumeHoverChange = onVolumeHoverChange
        )
    }
}

internal fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}
