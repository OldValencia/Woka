package com.woka.feature.viewer.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.woka.core.ui.theme.Theme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.data.PlaybackSpeed
import com.woka.feature.viewer.video.engine.VideoPlayerState

/**
 * Frame area past which raising the speed is visibly expensive. The cost that
 * caps playback is per-pixel — the RGBA conversion measured ~27ms a frame at
 * 3840x2160 against ~7ms at 1920x1080 — so area, not codec, is the honest test.
 * The threshold sits just under QHD, leaving 1080p and below unflagged (§6).
 */
private const val HEAVY_FRAME_PIXELS = 3_500_000

/**
 * Advisory under the speed slider (§6): on a large frame the decoder cannot
 * convert fast enough above 1x, so frames get dropped. That is deliberate — the
 * media clock and the sound stay correct and the picture pays instead — but
 * without a word here it just reads as the player misbehaving.
 */
@Composable
internal fun PlayerSpeedNotice(videoState: VideoPlayerState, speed: Float) {
    val track = videoState.tracks.mediaTracks.video
        .firstOrNull { it.index == videoState.tracks.selectedVideoIndex }
    val width = track?.width
    val height = track?.height
    val isHeavy = width != null && height != null && width * height >= HEAVY_FRAME_PIXELS
    val quality = buildString {
        append("${height}p")
        if (videoState.streamHdrInfo?.needsColorManagement == true) append(" HDR")
    }

    AnimatedVisibility(
        visible = isHeavy && speed > PlaybackSpeed.NORMAL,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Theme.viewer.onScrimHover)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            BasicText(
                text = "$quality — above 1x this file drops frames to keep the " +
                    "sound in step. Expected on a frame this large.",
                style = TextStyle(
                    color = Theme.viewer.onScrimSecondary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            )
        }
    }
}
