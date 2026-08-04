package com.woka.ui.viewer.player

import com.woka.AppDependencies
import com.woka.data.TrackPrefs
import com.woka.data.TrackRef

/**
 * Writing the remembered track choices (§2.6). Split out of `VideoPlayerTrackPrefs`
 * per the §2 file-size mandate.
 *
 * The two records are built separately (Step 15). The **file** record is a merge:
 * only the dimensions chosen in this file are refreshed, everything else keeps
 * whatever was stored, so an inherited value is never written back as if it had
 * been picked here. The **folder** record takes only what *this* action chose —
 * merging into it would let an untouched dimension revert the folder to a value
 * some other file set long ago.
 */
internal fun VideoPlayerState.persistTrackPrefs() {
    val folderPath = file.parent ?: return
    val repository = AppDependencies.settingsRepository
    val settings = repository.settings.value
    val chosen = tracks.chosen
    repository.setTrackPrefs(
        filePath = file.absolutePath,
        folderPath = folderPath,
        filePrefs = fileRecord(settings.trackPrefsPerFile[file.absolutePath] ?: TrackPrefs(), chosen),
        folderPrefs = folderRecord(settings.trackPrefsPerFolder[folderPath] ?: TrackPrefs(), chosen)
    )
}

private fun VideoPlayerState.fileRecord(
    stored: TrackPrefs,
    chosen: Set<TrackChoice>
): TrackPrefs {
    val media = tracks.mediaTracks
    val selection = tracks.subtitleSelection
    val external = selectedExternalSubtitle()
    val subtitleChosen = TrackChoice.Subtitle in chosen
    return stored.copy(
        audio = if (TrackChoice.Audio in chosen) {
            media.audio.refFor(tracks.selectedAudioIndex)
        } else {
            stored.audio
        },
        video = if (TrackChoice.Video in chosen) {
            media.video.refFor(tracks.selectedVideoIndex)
        } else {
            stored.video
        },
        subtitle = if (subtitleChosen) {
            media.subtitle.refFor(selection.embeddedIndex)
        } else {
            stored.subtitle
        },
        subtitleOff = if (subtitleChosen) selection == SubtitleSelection.Off else stored.subtitleOff,
        subtitleExternal = if (subtitleChosen) external?.path else stored.subtitleExternal,
        subtitleExternalLanguage = if (subtitleChosen) {
            external?.language
        } else {
            stored.subtitleExternalLanguage
        },
        subtitleExternalAdded = if (TrackChoice.Sidecars in chosen) {
            tracks.externalSubtitles.filter { it.isManual }.map { it.path }
        } else {
            stored.subtitleExternalAdded
        },
        subtitleExternalHidden = if (TrackChoice.Sidecars in chosen) {
            tracks.hiddenExternalSubtitles.toList()
        } else {
            stored.subtitleExternalHidden
        },
        subtitleDelayMs = if (TrackChoice.Sync in chosen) subtitleDelayMs else stored.subtitleDelayMs,
        audioDelayMs = if (TrackChoice.Sync in chosen) audioDelayMs else stored.audioDelayMs
    )
}

/**
 * The folder layer carries neither video (§2.6), nor the sync offsets (§3), nor a
 * sidecar path (Step 9): another episode's second video stream is a different
 * encode, its timing is its own, and its subtitle file is a different file. The
 * sidecar's *language* does carry — that is the preference worth keeping.
 */
private fun VideoPlayerState.folderRecord(
    stored: TrackPrefs,
    chosen: Set<TrackChoice>
): TrackPrefs {
    val selection = tracks.subtitleSelection
    val subtitleChosen = TrackChoice.Subtitle in chosen
    return stored.copy(
        audio = if (TrackChoice.Audio in chosen) {
            tracks.mediaTracks.audio.refFor(tracks.selectedAudioIndex)
        } else {
            stored.audio
        },
        subtitle = if (subtitleChosen) {
            tracks.mediaTracks.subtitle.refFor(selection.embeddedIndex)
        } else {
            stored.subtitle
        },
        subtitleOff = if (subtitleChosen) selection == SubtitleSelection.Off else stored.subtitleOff,
        subtitleExternalLanguage = if (subtitleChosen) {
            selectedExternalSubtitle()?.language
        } else {
            stored.subtitleExternalLanguage
        },
        video = null,
        subtitleExternal = null,
        subtitleExternalAdded = emptyList(),
        subtitleExternalHidden = emptyList(),
        subtitleDelayMs = 0L,
        audioDelayMs = 0L
    )
}

private fun List<MediaTrack>.refFor(index: Int?): TrackRef? =
    firstOrNull { it.index == index }?.toRef()

private fun VideoPlayerState.selectedExternalSubtitle(): ExternalSubtitle? =
    tracks.subtitleSelection.externalPath
        ?.let { path -> tracks.externalSubtitles.firstOrNull { it.path == path } }
