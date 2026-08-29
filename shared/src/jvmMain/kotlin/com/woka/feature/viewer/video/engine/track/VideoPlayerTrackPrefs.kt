package com.woka.feature.viewer.video.engine.track

import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.subtitle.describePickedSubtitle
import com.woka.feature.viewer.video.engine.subtitle.scanExternalSubtitles

import com.woka.app.AppDependencies
import com.woka.core.data.TrackPrefs
import java.io.File
import com.woka.feature.viewer.video.model.ExternalSubtitle
import com.woka.feature.viewer.video.model.MediaTracks
import com.woka.feature.viewer.video.model.SubtitleSelection

/**
 * Track persistence (§2.6): the one place selections are read on open and
 * written on change, for all three track types plus sidecar files (Step 9).
 */

/**
 * Restores the remembered tracks and sync offsets on open. Resolution is per-file
 * first, then the folder heuristic; the offsets (§3) come from the per-file record
 * only, since they describe one encode's timing rather than a series preference.
 *
 * Sidecars are scanned **before** [VideoPlayerTracks.apply], because a remembered
 * external subtitle can only be resolved against what is actually on disk.
 */
/**
 * The sidecar scan [applyTrackPrefs] needs, taken **off** the UI thread.
 *
 * Separate because the apply itself runs on Main (`writeUiState`) and this walks a
 * directory and stats files — on a network folder that is not something to do on the
 * event thread.
 */
internal fun VideoPlayerState.scanTrackSidecars(): List<ExternalSubtitle> {
    val perFile = AppDependencies.settingsRepository.settings.value
        .trackPrefsPerFile[file.absolutePath]
    return scanExternalSubtitles(file) + restoreAddedSubtitles(perFile)
}

/** [sidecars] comes from [scanTrackSidecars]; everything here is an assignment. */
internal fun VideoPlayerState.applyTrackPrefs(probed: MediaTracks, sidecars: List<ExternalSubtitle>) {
    val settings = AppDependencies.settingsRepository.settings.value
    val perFile = settings.trackPrefsPerFile[file.absolutePath]
    // The folder layer is a heuristic — "the last file watched here was Russian, so
    // this one probably is too" — and it is opt-in (§2.6). The record is still
    // written either way, so turning the setting on works from the next file rather
    // than from an empty history. The per-file record is never gated.
    val perFolder = if (settings.carryTracksAcrossFolder) {
        file.parent?.let { settings.trackPrefsPerFolder[it] }
    } else {
        null
    }
    tracks.hiddenExternalSubtitles = perFile?.subtitleExternalHidden.orEmpty().toSet()
    tracks.externalSubtitles = sidecars.filterNot { it.path in tracks.hiddenExternalSubtitles }
    tracks.apply(probed, perFile, perFolder)
    subtitleDelayMs = perFile?.subtitleDelayMs ?: 0L
    audioDelayMs = perFile?.audioDelayMs ?: 0L
}

/**
 * Hand-picked files remembered for this video, minus any that have since been
 * deleted or moved — a dead path in the list would offer a row that selects
 * nothing. Duplicates of an automatically scanned sidecar are dropped.
 */
private fun VideoPlayerState.restoreAddedSubtitles(perFile: TrackPrefs?): List<ExternalSubtitle> {
    val scanned = scanExternalSubtitles(file).map { it.path }.toSet()
    return perFile?.subtitleExternalAdded
        .orEmpty()
        .filter { it !in scanned }
        .map { File(it) }
        .filter { it.isFile }
        .map { describePickedSubtitle(it) }
}

/**
 * Selects a subtitle source — embedded stream, sidecar file, or Off (§2.2).
 *
 * The single write point the UI must use — writing `tracks.subtitleSelection`
 * directly skips persistence, which is exactly the asymmetry that hid the Step 2
 * audio regression. The subtitle decoder loop picks the change up from state, so
 * nothing else has to be signalled here.
 */
fun VideoPlayerState.selectSubtitle(selection: SubtitleSelection) {
    if (tracks.subtitleSelection == selection) return
    tracks.subtitleSelection = selection
    tracks.markChosen(TrackChoice.Subtitle)
    persistTrackPrefs()
}

/**
 * Adds a hand-picked subtitle file and selects it. Files outside the video's own
 * folder are kept too — the picker exists precisely for the case where the
 * automatic scan found nothing.
 */
fun VideoPlayerState.addExternalSubtitle(path: String) {
    val picked = File(path)
    if (!picked.isFile) return
    val absolute = picked.absolutePath
    // Adding is also how a hidden file comes back — that is the only route back,
    // so it has to clear the record rather than be blocked by it.
    tracks.hiddenExternalSubtitles = tracks.hiddenExternalSubtitles - absolute
    if (tracks.externalSubtitles.none { it.path == absolute }) {
        tracks.externalSubtitles = tracks.externalSubtitles + describePickedSubtitle(picked)
    }
    tracks.subtitleSelection = SubtitleSelection.External(absolute)
    tracks.markChosen(TrackChoice.Subtitle)
    tracks.markChosen(TrackChoice.Sidecars)
    persistTrackPrefs()
}

/**
 * Dismisses a sidecar from the list — **any** sidecar, scanned or hand-picked.
 * The earlier cut, where only hand-picked entries could go, drew the line by where
 * the file sits rather than by anything visible: two identical-looking rows, one
 * removable and one not, for a reason nothing on screen explains.
 *
 * The file on disk is never touched. Re-adding it through the picker brings it back.
 */
fun VideoPlayerState.hideExternalSubtitle(path: String) {
    if (tracks.externalSubtitles.none { it.path == path }) return
    tracks.hiddenExternalSubtitles = tracks.hiddenExternalSubtitles + path
    tracks.externalSubtitles = tracks.externalSubtitles.filterNot { it.path == path }
    // Hiding what is currently showing has to turn subtitles off, or the overlay
    // would keep rendering cues from a source no longer in the list.
    if (tracks.subtitleSelection.externalPath == path) {
        tracks.subtitleSelection = SubtitleSelection.Off
        tracks.markChosen(TrackChoice.Subtitle)
    }
    tracks.markChosen(TrackChoice.Sidecars)
    persistTrackPrefs()
}
