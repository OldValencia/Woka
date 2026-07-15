package io.iris.ui.viewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
fun PlayPauseButton(videoState: VideoPlayerState?, isPlaying: Boolean) {
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
}

@Composable
fun SkipBackwardButton(videoState: VideoPlayerState?, currentMs: Long) {
    SimpleTooltip(text = "Skip Backward 5s") {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }) {
                    videoState?.seek(
                        (currentMs - 5000).coerceAtLeast(0)
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

@Composable
fun SkipForwardButton(videoState: VideoPlayerState?, currentMs: Long, durationMs: Long) {
    SimpleTooltip(text = "Skip Forward 5s") {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }) {
                    videoState?.seek(
                        (currentMs + 5000).coerceAtMost(durationMs)
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

@Composable
fun RepeatButton(videoState: VideoPlayerState?) {
    SimpleTooltip("Repeat") {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { videoState?.let { it.isRepeatMode = !it.isRepeatMode } },
            contentAlignment = Alignment.Center
        ) {
            val repeatMode = videoState?.isRepeatMode == true
            Icon(
                painter = painterResource(if (repeatMode) IrisIcons.Static.Undo else IrisIcons.Static.Redo),
                contentDescription = "Repeat",
                tint = if (repeatMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
