package com.woka.ui.viewer.player

import com.woka.AppDependencies
import com.woka.data.TrackPrefs
import java.io.File

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
internal fun VideoPlayerState.applyTrackPrefs(probed: MediaTracks) {
    val settings = AppDependencies.settingsRepository.settings.value
    val perFile = settings.trackPrefsPerFile[file.absolutePath]
    val perFolder = file.parent?.let { settings.trackPrefsPerFolder[it] }
    tracks.hiddenExternalSubtitles = perFile?.subtitleExternalHidden.orEmpty().toSet()
    tracks.externalSubtitles = (scanExternalSubtitles(file) + restoreAddedSubtitles(perFile))
        .filterNot { it.path in tracks.hiddenExternalSubtitles }
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
 * Writes the current selection back to settings. Both layers are written on every
 * manual switch: the per-file record is authoritative when this exact file is
 * re-opened, the per-folder one seeds the next episode in the same folder.
 */
internal fun VideoPlayerState.persistTrackPrefs() {
    val folderPath = file.parent ?: return
    val media = tracks.mediaTracks
    val selection = tracks.subtitleSelection
    val external = selection.externalPath
        ?.let { path -> tracks.externalSubtitles.firstOrNull { it.path == path } }
    val prefs = TrackPrefs(
        audio = media.audio.firstOrNull { it.index == tracks.selectedAudioIndex }?.toRef(),
        subtitle = media.subtitle
            .firstOrNull { it.index == selection.embeddedIndex }
            ?.toRef(),
        subtitleOff = selection == SubtitleSelection.Off,
        video = media.video.firstOrNull { it.index == tracks.selectedVideoIndex }?.toRef(),
        subtitleExternal = external?.path,
        subtitleExternalLanguage = external?.language,
        subtitleExternalAdded = tracks.externalSubtitles.filter { it.isManual }.map { it.path },
        subtitleExternalHidden = tracks.hiddenExternalSubtitles.toList(),
        subtitleDelayMs = subtitleDelayMs,
        audioDelayMs = audioDelayMs
    )
    AppDependencies.settingsRepository.setTrackPrefs(file.absolutePath, folderPath, prefs)
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
    }
    persistTrackPrefs()
}
