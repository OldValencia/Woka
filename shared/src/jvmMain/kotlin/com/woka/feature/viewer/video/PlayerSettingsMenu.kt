package com.woka.feature.viewer.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.woka.app.AppDependencies
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.track.resetTracksForThisFile
import com.woka.feature.viewer.video.engine.track.selectVideoTrack
import com.woka.feature.viewer.shell.PopoverListRow
import com.woka.feature.viewer.shell.PopoverSectionLabel

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
    val repo = remember { AppDependencies.settingsRepository }
    val settings by repo.settings.collectAsState()
    val videoTracks = videoState.tracks.mediaTracks.video
    // A single video stream is the overwhelmingly common case; showing a
    // one-entry track list there would be noise, so it stays hidden (§2.3).
    val showVideoTracks = videoTracks.size > 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
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
        PlaybackModeRows(
            repeat = settings.repeatMode,
            shuffle = settings.shuffle,
            showRemainingTime = settings.showRemainingTime,
            onRepeatCycle = { repo.setRepeatMode(settings.repeatMode.next()) },
            onShuffleToggle = { repo.setShuffle(!settings.shuffle) },
            onTimeLabelToggle = { repo.setShowRemainingTime(!settings.showRemainingTime) }
        )
        PlayerSpeedSlider(
            speed = settings.playbackSpeed,
            onSpeedChange = { repo.setPlaybackSpeed(it) }
        )
        PlayerSpeedNotice(videoState, settings.playbackSpeed)
        PlayerChapterSection(videoState)
        PlayerSyncSection(videoState)
        // The same global flag the Settings panel writes — a second way in, not a
        // per-file override. Only does anything in fullscreen, where the bars are.
        PopoverSectionLabel("Picture")
        PopoverListRow(
            label = "Ambient fill",
            selected = settings.ambientFill,
            onClick = { repo.setAmbientFill(!settings.ambientFill) }
        )
        // The per-file record wins over the folder layer, so a file configured once
        // keeps its choice forever. This is the way back (Step 15) — it lives here
        // rather than in the subtitle popover, which is a list of tracks, not a
        // place for a destructive action.
        PopoverSectionLabel("Tracks")
        PopoverResetRow(
            label = "Reset tracks for this file",
            enabled = settings.trackPrefsPerFile.containsKey(videoState.file.absolutePath),
            onReset = { videoState.resetTracksForThisFile() },
            disabledHint = "Nothing to reset: no track was chosen in this file, " +
                "so it already opens on the folder or container default"
        )
    }
}
