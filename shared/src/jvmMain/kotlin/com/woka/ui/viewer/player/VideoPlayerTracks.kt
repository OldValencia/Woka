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
/**
 * The dimensions a per-file record can hold, tracked so that only the ones the
 * user actually touched *in this file* are written (Step 15).
 */
enum class TrackChoice { Audio, Subtitle, Video, Sync, Sidecars }

@Stable
class VideoPlayerTracks {
    /**
     * What was explicitly chosen in this file during this session, plus whatever
     * the stored record already held. Anything outside it is inherited, and
     * inherited values must not be written back as if they had been picked.
     */
    var chosen by mutableStateOf<Set<TrackChoice>>(emptySet())
        private set

    fun markChosen(choice: TrackChoice) {
        chosen = chosen + choice
    }

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
     * Applies the probe result, restoring remembered selections (§2.6).
     *
     * Resolution is **per dimension**, not per record (Step 15): audio takes the
     * per-file choice if there is one, else the folder's, else the container
     * default, and subtitles do the same independently. Video resolves from
     * [perFile] only — carrying a video track across episodes is meaningless.
     *
     * Set [externalSubtitles] before calling this: a restored sidecar has to be
     * resolvable against the list that was actually found on disk.
     */
    fun apply(tracks: MediaTracks, perFile: TrackPrefs? = null, perFolder: TrackPrefs? = null) {
        mediaTracks = tracks
        hasSubtitle = tracks.hasSubtitle
        chosen = choicesIn(perFile)
        selectedVideoIndex = tracks.video.matchRef(perFile?.video)?.index
            ?: tracks.video.defaultIndex()
        selectedAudioIndex = tracks.audio.matchRef(perFile?.audio ?: perFolder?.audio)?.index
            ?: tracks.audio.defaultIndex()
        subtitleSelection = resolveSubtitle(tracks, perFile, perFolder)
    }

    /** What the stored record already claims was chosen here, so a later write keeps it. */
    private fun choicesIn(perFile: TrackPrefs?): Set<TrackChoice> {
        if (perFile == null) return emptySet()
        return buildSet {
            if (perFile.audio != null) add(TrackChoice.Audio)
            if (perFile.hasSubtitleChoice) add(TrackChoice.Subtitle)
            if (perFile.video != null) add(TrackChoice.Video)
            if (perFile.subtitleDelayMs != 0L || perFile.audioDelayMs != 0L) add(TrackChoice.Sync)
            if (perFile.subtitleExternalAdded.isNotEmpty() ||
                perFile.subtitleExternalHidden.isNotEmpty()
            ) {
                add(TrackChoice.Sidecars)
            }
        }
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
        // One layer answers the whole subtitle question: the file's if it made a
        // choice at all, otherwise the folder's. Mixing the two would let a file
        // that only turned subs *off* still inherit the folder's track.
        val layer = perFile?.takeIf { it.hasSubtitleChoice }
            ?: perFolder
            ?: return SubtitleSelection.Off
        if (layer.subtitleOff == true) return SubtitleSelection.Off

        layer.subtitleExternal
            ?.let { path -> externalSubtitles.firstOrNull { it.path == path } }
            ?.let { return SubtitleSelection.External(it.path) }

        layer.subtitleExternalLanguage
            ?.let { lang -> externalSubtitles.firstOrNull { it.language == lang } }
            ?.let { return SubtitleSelection.External(it.path) }

        return tracks.subtitle.matchRef(layer.subtitle)?.index
            ?.let { SubtitleSelection.Embedded(it) }
            ?: SubtitleSelection.Off
    }

    private fun List<MediaTrack>.defaultIndex(): Int? =
        (firstOrNull { it.isDefault } ?: firstOrNull())?.index
}
