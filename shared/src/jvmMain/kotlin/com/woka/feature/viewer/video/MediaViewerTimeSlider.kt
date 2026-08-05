package com.woka.feature.viewer.video

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.pauseForSeek
import com.woka.feature.viewer.video.engine.resumeAfterSeek
import com.woka.feature.viewer.video.engine.seek
import com.woka.feature.viewer.video.engine.skipBy
import org.jetbrains.compose.resources.painterResource
import com.woka.feature.viewer.shell.slider.MediaSlider
import com.woka.feature.viewer.shell.slider.defaultMediaSliderStyle

@Composable
fun MediaViewerTimeSlider(
    videoState: VideoPlayerState?,
    currentMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    // Knobs for the skip buttons, shared by both. `iconWidth`/`iconHeight` set
    // the drawn glyph independently — the SVG is stretched to fill them
    // (ContentScale.FillBounds), so non-equal values distort the shape on
    // purpose. `hitPadding` is the extra clickable margin around the glyph, so
    // the click area = icon + 2*padding (the box wraps the padded icon, so the
    // icon size is never clamped).
    val iconWidth = 20.dp
    val iconHeight = 23.dp
    val hitPadding = 4.dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        Text(
            formatTime(currentMs),
            color = Theme.colors.textPrimary,
            style = TextStyle(fontSize = 13.sp, color = Theme.colors.textPrimary)
        )

        if (durationMs >= 6000L) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = videoState != null) {
                        videoState?.skipBy(-5000)
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(Icons.Static.SkipBackward5),
                    contentDescription = "Skip back 5s",
                    contentScale = ContentScale.FillBounds,
                    colorFilter = ColorFilter.tint(Theme.colors.textPrimary),
                    modifier = Modifier.padding(hitPadding).width(iconWidth).height(iconHeight)
                )
            }
        }

        var isDragging by remember { mutableStateOf(false) }
        var sliderValue by remember { mutableStateOf(0f) }

        MediaSlider(
            value = if (isDragging) sliderValue else if (durationMs > 0) (currentMs.toFloat() / durationMs.toFloat()).coerceIn(
                0f,
                1f
            ) else 0f,
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
            tooltipText = { formatTime((it * durationMs).toLong()) },
            segments = chapterSegments(videoState?.tracks?.chapters.orEmpty(), durationMs),
            style = defaultMediaSliderStyle(),
            modifier = Modifier.weight(1f)
        )

        if (durationMs >= 6000L) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = videoState != null) {
                        videoState?.skipBy(5000)
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(Icons.Static.SkipForward5),
                    contentDescription = "Skip forward 5s",
                    contentScale = ContentScale.FillBounds,
                    colorFilter = ColorFilter.tint(Theme.colors.textPrimary),
                    modifier = Modifier.padding(hitPadding).width(iconWidth).height(iconHeight)
                )
            }
        }

        Text(
            formatTime(durationMs),
            color = Theme.colors.textPrimary,
            style = TextStyle(fontSize = 13.sp, color = Theme.colors.textPrimary)
        )
    }
}

internal fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}
