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

    // Off is the default on first open (§2.2).
    var subtitleSelection by mutableStateOf<SubtitleSelection>(SubtitleSelection.Off)
    var activeCue by mutableStateOf<Cue?>(null)

    /** Sidecar files found next to the video (Step 9), kept apart from [mediaTracks]. */
    var externalSubtitles by mutableStateOf<List<ExternalSubtitle>>(emptyList())

    /** Paths dismissed from [externalSubtitles]; the file on disk is untouched. */
    var hiddenExternalSubtitles by mutableStateOf<Set<String>>(emptySet())

    /** True when there is anything at all to choose from, embedded or sidecar. */
    val hasAnySubtitle: Boolean
        get() = hasSubtitle || externalSubtitles.isNotEmpty()

    /**
     * Applies the probe result, restoring remembered selections (§2.6): the
     * per-file record wins, else the folder heuristic, else the container's own
     * default disposition. Video resolves from [perFile] only — carrying a video
     * track across episodes is meaningless.
     *
     * Set [externalSubtitles] before calling this: a restored sidecar has to be
     * resolvable against the list that was actually found on disk.
     */
    fun apply(tracks: MediaTracks, perFile: TrackPrefs? = null, perFolder: TrackPrefs? = null) {
        mediaTracks = tracks
        hasSubtitle = tracks.hasSubtitle
        val prefs = perFile ?: perFolder
        selectedVideoIndex = tracks.video.matchRef(perFile?.video)?.index
            ?: tracks.video.defaultIndex()
        selectedAudioIndex = tracks.audio.matchRef(prefs?.audio)?.index
            ?: tracks.audio.defaultIndex()
        subtitleSelection = resolveSubtitle(tracks, perFile, perFolder)
    }

    /**
     * Sidecar first, and by two different keys per layer. The per-file record
     * stores the path, which identifies exactly one file; the folder record stores
     * only the *language*, because a path is meaningless on the next episode —
     * that file has its own sidecar, and what carries over is "the user watches
     * this folder with external Russian".
     */
    private fun resolveSubtitle(
        tracks: MediaTracks,
        perFile: TrackPrefs?,
        perFolder: TrackPrefs?
    ): SubtitleSelection {
        val prefs = perFile ?: perFolder
        if (prefs == null || prefs.subtitleOff) return SubtitleSelection.Off

        perFile?.subtitleExternal
            ?.let { path -> externalSubtitles.firstOrNull { it.path == path } }
            ?.let { return SubtitleSelection.External(it.path) }

        prefs.subtitleExternalLanguage
            ?.let { lang -> externalSubtitles.firstOrNull { it.language == lang } }
            ?.let { return SubtitleSelection.External(it.path) }

        return tracks.subtitle.matchRef(prefs.subtitle)?.index
            ?.let { SubtitleSelection.Embedded(it) }
            ?: SubtitleSelection.Off
    }

    private fun List<MediaTrack>.defaultIndex(): Int? =
        (firstOrNull { it.isDefault } ?: firstOrNull())?.index
}
