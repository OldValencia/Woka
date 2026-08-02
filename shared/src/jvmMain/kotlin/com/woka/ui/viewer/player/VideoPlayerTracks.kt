package com.woka.ui.viewer.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Track state split out of [VideoPlayerState] to keep it under the file-size
 * mandate (§2). Holds the enumerated [MediaTracks] plus the currently selected
 * stream indices.
 *
 * Selection is applied to the grabber in a later step (§2 Step 2); here it only
 * records intent from the probe defaults (§2.0 Step 0).
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

    fun apply(tracks: MediaTracks) {
        mediaTracks = tracks
        hasSubtitle = tracks.hasSubtitle
        selectedVideoIndex = tracks.video.defaultIndex()
        selectedAudioIndex = tracks.audio.defaultIndex()
        selectedSubtitleIndex = null
    }

    private fun List<MediaTrack>.defaultIndex(): Int? =
        (firstOrNull { it.isDefault } ?: firstOrNull())?.index
}
