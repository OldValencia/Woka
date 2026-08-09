package com.woka.feature.viewer.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.core.ui.theme.Theme
import com.woka.feature.viewer.video.engine.VideoPlayerState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** Long enough to read twice, short enough not to sit over the picture. */
private const val VISIBLE_MS = 8_000L

/**
 * Says, once, that this machine is not keeping up with this file.
 *
 * Playback already degrades correctly on its own — frames are dropped so the media
 * clock and the sound stay right, and the picture pays instead. That is the correct
 * trade and every player makes it, but without a word here it just reads as the
 * player misbehaving. `PlayerSpeedNotice` says the same thing for playback above
 * 1x; this is the same courtesy for a machine that is simply too slow.
 *
 * The numbers are quoted because they are honest and checkable, and because "about
 * 11 of 24" tells the user something a vague warning does not.
 *
 * Shown **once per file** and then dismissed on a timer: a message that keeps
 * returning is worse than no message, and there is nothing the user can act on a
 * second time.
 */
@Composable
internal fun PlaybackHealthNotice(videoState: VideoPlayerState, bottomPadding: Dp) {
    val health = videoState.health
    // The only frame-rate-adjacent state the tree reads, and it flips at most twice
    // in the life of a file — see [PlaybackHealth] for why nothing else may be.
    val struggling = health.isStruggling
    var visible by remember(videoState) { mutableStateOf(false) }

    LaunchedEffect(videoState, struggling) {
        if (!struggling) return@LaunchedEffect
        visible = true
        delay(VISIBLE_MS.milliseconds)
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = bottomPadding),
            contentAlignment = Alignment.BottomCenter
        ) {
            NoticeCard(text = noticeText(videoState, health.displayedFps, health.expectedFps))
        }
    }
}

/**
 * Reads the resolution from the track rather than the decoded frame: the frame may
 * be decoded smaller than native to fit the surface (§6), and what the user needs
 * to hear about is the file, not the current window.
 */
private fun noticeText(videoState: VideoPlayerState, displayed: Int, expected: Int): String {
    val track = videoState.tracks.mediaTracks.video
        .firstOrNull { it.index == videoState.tracks.selectedVideoIndex }
    val quality = buildString {
        track?.height?.let { append("${it}p") }
        if (videoState.streamHdrInfo?.needsColorManagement == true) append(" HDR")
    }.trim()
    val subject = if (quality.isEmpty()) "This file" else "$quality — this file"
    return "$subject is more than this machine can play smoothly: showing about " +
        "$displayed of $expected frames a second. The sound stays in step; the picture pays."
}

@Composable
private fun NoticeCard(text: String) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .clip(shape)
            .background(Theme.viewer.tooltipScrim)
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        )
    }
}
