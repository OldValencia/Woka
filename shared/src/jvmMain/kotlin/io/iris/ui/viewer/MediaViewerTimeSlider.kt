package io.iris.ui.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.iris.ui.icons.IrisIcons
import io.iris.ui.theme.IrisTheme
import io.iris.ui.viewer.player.VideoPlayerState
import org.jetbrains.compose.resources.painterResource
import kotlin.math.max
import kotlin.math.min

@Composable
fun MediaViewerTimeSlider(
    videoState: VideoPlayerState?,
    currentMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier, 
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        Text(formatTime(currentMs), color = IrisTheme.colors.textPrimary, style = TextStyle(fontSize = 13.sp, color = IrisTheme.colors.textPrimary))
        
        if (durationMs >= 6000L) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = videoState != null) { 
                        videoState?.seek(max(0, currentMs - 5000))
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(IrisIcons.Static.SkipBackward5), "Skip back 5s", tint = IrisTheme.colors.textPrimary, modifier = Modifier.width(20.dp).height(20.dp))
            }
        }
        
        var isDragging by remember { mutableStateOf(false) }
        var sliderValue by remember { mutableStateOf(0f) }

        MediaSlider(
            value = if (isDragging) sliderValue else if (durationMs > 0) (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
            onValueChange = {
                if (!isDragging) {
                    isDragging = true
                    videoState?.pauseForSeek()
                }
                sliderValue = it
            },
            onValueChangeFinished = {
                if (isDragging) {
                    videoState?.seek((sliderValue * durationMs).toLong())
                    videoState?.resumeAfterSeek()
                    isDragging = false
                }
            },
            activeColor = IrisTheme.colors.accent,
            inactiveColor = IrisTheme.colors.textSecondary,
            thumbColor = IrisTheme.colors.accent,
            modifier = Modifier.weight(1f)
        )
        
        if (durationMs >= 6000L) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = videoState != null) { 
                        videoState?.seek(min(durationMs, currentMs + 5000))
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(IrisIcons.Static.SkipForward5), "Skip forward 5s", tint = IrisTheme.colors.textPrimary, modifier = Modifier.width(20.dp).height(20.dp))
            }
        }
        
        Text(formatTime(durationMs), color = IrisTheme.colors.textPrimary, style = TextStyle(fontSize = 13.sp, color = IrisTheme.colors.textPrimary))
    }
}

internal fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}
