package com.woka.ui.viewer.player

import com.woka.AppDependencies
import com.woka.data.TrackPrefs

/**
 * Track persistence (§2.6): the one place selections are read on open and
 * written on change, for all three track types.
 */

/**
 * Restores the remembered tracks and sync offsets on open. Resolution is per-file
 * first, then the folder heuristic; the offsets (§3) come from the per-file record
 * only, since they describe one encode's timing rather than a series preference.
 */
internal fun VideoPlayerState.applyTrackPrefs(probed: MediaTracks) {
    val settings = AppDependencies.settingsRepository.settings.value
    val perFile = settings.trackPrefsPerFile[file.absolutePath]
    val perFolder = file.parent?.let { settings.trackPrefsPerFolder[it] }
    tracks.apply(probed, perFile, perFolder)
    subtitleDelayMs = perFile?.subtitleDelayMs ?: 0L
    audioDelayMs = perFile?.audioDelayMs ?: 0L
}

/**
 * Writes the current selection back to settings. Both layers are written on every
 * manual switch: the per-file record is authoritative when this exact file is
 * re-opened, the per-folder one seeds the next episode in the same folder.
 */
internal fun VideoPlayerState.persistTrackPrefs() {
    val folderPath = file.parent ?: return
    val media = tracks.mediaTracks
    val prefs = TrackPrefs(
        audio = media.audio.firstOrNull { it.index == tracks.selectedAudioIndex }?.toRef(),
        subtitle = media.subtitle
            .firstOrNull { it.index == tracks.selectedSubtitleIndex }
            ?.toRef(),
        subtitleOff = tracks.selectedSubtitleIndex == null,
        video = media.video.firstOrNull { it.index == tracks.selectedVideoIndex }?.toRef(),
        subtitleDelayMs = subtitleDelayMs,
        audioDelayMs = audioDelayMs
    )
    AppDependencies.settingsRepository.setTrackPrefs(file.absolutePath, folderPath, prefs)
}

/**
 * Selects a subtitle track by absolute stream index, or null for Off (§2.2).
 *
 * The single write point the UI must use — writing `tracks.selectedSubtitleIndex`
 * directly skips persistence, which is exactly the asymmetry that hid the Step 2
 * audio regression. The subtitle decoder loop picks the change up from state, so
 * nothing else has to be signalled here.
 */
fun VideoPlayerState.selectSubtitleTrack(index: Int?) {
    if (tracks.selectedSubtitleIndex == index) return
    tracks.selectedSubtitleIndex = index
    persistTrackPrefs()
}
