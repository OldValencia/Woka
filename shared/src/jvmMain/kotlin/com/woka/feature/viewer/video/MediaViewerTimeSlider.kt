package com.woka.feature.viewer.video

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.icons.Icons
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.pauseForSeek
import com.woka.feature.viewer.video.engine.preview.rememberSeekPreview
import com.woka.feature.viewer.video.engine.resumeAfterSeek
import com.woka.feature.viewer.video.engine.seek
import com.woka.feature.viewer.video.engine.skipBy
import com.woka.feature.viewer.shell.slider.MediaSlider
import com.woka.feature.viewer.shell.slider.SliderPreview
import com.woka.feature.viewer.shell.slider.defaultMediaSliderStyle

/**
 * [position] stays a lambda through this whole function: invoking it here would
 * put the play-head read in this scope and recompose the skip buttons and their
 * `painterResource` lookups on every presented frame (§10.5). It is called only
 * inside [TimeLabel] and inside [MediaSlider], the two places that move.
 */
@Composable
fun MediaViewerTimeSlider(
    videoState: VideoPlayerState?,
    position: () -> Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
    // True while the pointer is on the bar, hovering or dragging. The chrome uses it
    // to stay put: a pointer parked on the seek bar is the UI being read, not idle.
    onHoverChange: (Boolean) -> Unit = {}
) {
    // Null unless there is a video with a known duration. Owns two grabbers and one
    // low-priority thread; see the package for why previews can never outrank the
    // picture.
    val preview = rememberSeekPreview(videoState, durationMs)
    val previewAspect = videoState?.previewAspect()
    // Tone-mapped when the stream is HDR, exactly as the played frame is.
    val previewPainter = rememberPreviewPainter(videoState, preview?.shown)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        // Deliberately the player's own position, not the drag target: while
        // scrubbing the label holds and the tooltip shows where the release lands.
        TimeLabel(position)

        if (durationMs >= 6000L) {
            SkipButton(
                icon = Icons.Static.SkipBackward5,
                contentDescription = "Skip back 5s",
                enabled = videoState != null,
                onClick = { videoState?.skipBy(-5000) }
            )
        }

        var isDragging by remember { mutableStateOf(false) }
        var sliderValue by remember { mutableStateOf(0f) }

        MediaSlider(
            value = {
                if (isDragging) {
                    sliderValue
                } else if (durationMs > 0) {
                    (position().toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
            },
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
            // The aspect is supplied only once something has been shown, so a file
            // that never produces a still keeps a plain pill rather than a permanent
            // empty hole — that field is what turns the pill into a frame window.
            tooltipPreview = SliderPreview(
                painter = previewPainter,
                aspect = previewAspect?.takeIf { preview?.hasShown == true }
            ),
            onTooltipPositionChange = { fraction ->
                onHoverChange(fraction != null)
                preview?.request(fraction?.let { (it * durationMs).toLong() })
            },
            segments = chapterSegments(videoState?.tracks?.chapters.orEmpty(), durationMs),
            style = defaultMediaSliderStyle(),
            modifier = Modifier.weight(1f)
        )

        if (durationMs >= 6000L) {
            SkipButton(
                icon = Icons.Static.SkipForward5,
                contentDescription = "Skip forward 5s",
                enabled = videoState != null,
                onClick = { videoState?.skipBy(5000) }
            )
        }

        TimeLabel { durationMs }
    }
}

/**
 * The media's own aspect, so a 2.39:1 film is not letterboxed inside a 16:9 hole.
 * Null until the tracks are probed, which is also what keeps the pill text-only
 * before there is anything to show.
 */
private fun VideoPlayerState.previewAspect(): Float? {
    val track = tracks.mediaTracks.video.firstOrNull { it.index == tracks.selectedVideoIndex }
    val width = track?.width ?: return null
    val height = track.height ?: return null
    return if (width > 0 && height > 0) width.toFloat() / height else null
}

/** Calls [position] in its own scope, so a moving play head recomposes only this. */
@Composable
private fun TimeLabel(position: () -> Long) {
    Text(
        formatTime(position()),
        color = Theme.colors.textPrimary,
        // 15sp against the row's 16–20dp icons; at 13 it read as a caption beside them.
        style = TextStyle(fontSize = 15.sp, color = Theme.colors.textPrimary)
    )
}

internal fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}
