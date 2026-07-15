package io.iris.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.iris.ui.viewer.player.VideoPlayerState

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
        val currentMs = videoState?.currentPositionMs ?: 0L
        val durationMs = videoState?.durationMs ?: 0L

        PlayPauseButton(videoState, isPlaying)

        Text(
            formatTime(currentMs),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium
        )

        if (durationMs >= 6000L) {
            SkipBackwardButton(videoState, currentMs)
        }

        MediaSlider(
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
            SkipForwardButton(videoState, currentMs, durationMs)
        }

        Text(
            formatTime(durationMs),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium
        )

        Spacer(Modifier.width(8.dp))

        RepeatButton(videoState)

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
