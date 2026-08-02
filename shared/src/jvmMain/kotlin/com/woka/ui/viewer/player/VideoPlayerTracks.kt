package com.woka.ui.viewer.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.woka.data.TrackPrefs

/**
 * Track state split out of [VideoPlayerState] to keep it under the file-size
 * mandate (§2). Holds the enumerated [MediaTracks] plus the currently selected
 * stream indices.
 *
 * Selections are pushed to the grabbers by `selectAudioTrack` / `selectVideoTrack`
 * / `selectSubtitleTrack`; [apply] seeds them on open from the probe and the
 * remembered records (§2.6).
 */
@Stable
class VideoPlayerTracks {
    var mediaTracks by mutableStateOf(MediaTracks.EMPTY)
        private set
    var hasSubtitle by mutableStateOf(false)
        private set

    var selectedVideoIndex by mutableStateOf<Int?>(null)
    var selectedAudioIndex by mutableStateOf<Int?>(null)

    // null == subtitles Off, which is the default on first open (§2.2).
    var selectedSubtitleIndex by mutableStateOf<Int?>(null)
    var activeCue by mutableStateOf<Cue?>(null)

    /**
     * Applies the probe result, restoring remembered selections (§2.6): the
     * per-file record wins, else the folder heuristic, else the container's own
     * default disposition. Video resolves from [perFile] only — carrying a video
     * track across episodes is meaningless.
     */
    fun apply(tracks: MediaTracks, perFile: TrackPrefs? = null, perFolder: TrackPrefs? = null) {
        mediaTracks = tracks
        hasSubtitle = tracks.hasSubtitle
        val prefs = perFile ?: perFolder
        selectedVideoIndex = tracks.video.matchRef(perFile?.video)?.index
            ?: tracks.video.defaultIndex()
        selectedAudioIndex = tracks.audio.matchRef(prefs?.audio)?.index
            ?: tracks.audio.defaultIndex()
        // Subtitles stay Off (§2.2) unless a record explicitly says they were on.
        selectedSubtitleIndex = if (prefs == null || prefs.subtitleOff) {
            null
        } else {
            tracks.subtitle.matchRef(prefs.subtitle)?.index
        }
    }

    private fun List<MediaTrack>.defaultIndex(): Int? =
        (firstOrNull { it.isDefault } ?: firstOrNull())?.index
}
