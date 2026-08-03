package com.woka.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.woka.ui.viewer.player.VideoPlayerState
import com.woka.ui.viewer.player.selectVideoTrack

/**
 * Contents of the Settings ⚙ popover (§2.4) — the shared home for secondary
 * playback controls. Carries Video Track selection (§2.3) and the sync offsets
 * (§3); Repeat, Shuffle and Playback Speed (§6) land here later.
 *
 * The icon is always visible, so the menu must render something even when the
 * file offers nothing — an audio-less, subtitle-less single-video file.
 */
@Composable
internal fun PlayerSettingsMenuList(videoState: VideoPlayerState) {
    val videoTracks = videoState.tracks.mediaTracks.video
    // A single video stream is the overwhelmingly common case; showing a
    // one-entry track list there would be noise, so it stays hidden (§2.3).
    val showVideoTracks = videoTracks.size > 1
    val showSync = videoState.hasSubtitle || videoState.hasAudio

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (showVideoTracks) {
            PopoverSectionLabel("Video Track")
            videoTracks.forEach { track ->
                PopoverListRow(
                    label = track.label,
                    selected = track.index == videoState.tracks.selectedVideoIndex,
                    onClick = { videoState.selectVideoTrack(track.index) }
                )
            }
        }
        PlayerSyncSection(videoState)
        if (!showVideoTracks && !showSync) {
            Text(
                text = "No additional options",
                color = Color.White.copy(alpha = 0.45f),
                style = TextStyle(fontSize = 12.sp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}
