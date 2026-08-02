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
import com.woka.ui.viewer.player.MediaTrack

/**
 * Contents of the Settings ⚙ popover (§2.4) — the shared home for secondary
 * playback controls. Today it carries Video Track selection (§2.3); Repeat,
 * Shuffle and Playback Speed (§6) land here later.
 *
 * The icon is always visible, so the menu must render something even when the
 * file offers no options at all.
 */
@Composable
internal fun PlayerSettingsMenuList(
    videoTracks: List<MediaTrack>,
    selectedVideoIndex: Int?,
    onSelectVideo: (Int) -> Unit
) {
    // A single video stream is the overwhelmingly common case; showing a
    // one-entry track list there would be noise, so it stays hidden (§2.3).
    val showVideoTracks = videoTracks.size > 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (showVideoTracks) {
            PopoverSectionLabel("Video Track")
            videoTracks.forEach { track ->
                PopoverListRow(
                    label = track.label,
                    selected = track.index == selectedVideoIndex,
                    onClick = { onSelectVideo(track.index) }
                )
            }
        } else {
            Text(
                text = "No additional options",
                color = Color.White.copy(alpha = 0.45f),
                style = TextStyle(fontSize = 12.sp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}
