package com.woka.ui.viewer.player

import com.woka.AppDependencies

/**
 * Drops this file's own track record and re-resolves it as if it had never been
 * configured — from the folder layer if that is on, otherwise from the container's
 * defaults (Step 15).
 *
 * The escape hatch the per-file-wins rule needs: without it, a file that was ever
 * configured by hand keeps its choice forever, and changing your mind about a whole
 * season would mean visiting every such file.
 *
 * Applied live rather than on the next open, and deliberately **without**
 * persisting: writing here would immediately recreate the record that was just
 * dropped.
 */
fun VideoPlayerState.resetTracksForThisFile() {
    val repository = AppDependencies.settingsRepository
    repository.clearTrackPrefsForFile(file.absolutePath)
    val settings = repository.settings.value
    val perFolder = if (settings.carryTracksAcrossFolder) {
        file.parent?.let { settings.trackPrefsPerFolder[it] }
    } else {
        null
    }

    val previousAudio = tracks.selectedAudioIndex
    val previousVideo = tracks.selectedVideoIndex
    // The ✕ records go with the rest of the file's state, so hidden sidecars come
    // back and the list is what a first-ever open would have found.
    tracks.hiddenExternalSubtitles = emptySet()
    tracks.externalSubtitles = scanExternalSubtitles(file)
    tracks.apply(tracks.mediaTracks, perFile = null, perFolder = perFolder)

    if (tracks.selectedAudioIndex != previousAudio) {
        pendingAudioReopenIndex = tracks.selectedAudioIndex
    }
    if (tracks.selectedVideoIndex != previousVideo) {
        isReloadingVideo = true
        pendingVideoReopenIndex = tracks.selectedVideoIndex
    }
    if (subtitleDelayMs != 0L || audioDelayMs != 0L) {
        subtitleDelayMs = 0L
        audioDelayMs = 0L
        // The audio loop only re-seeks on this generation; waiting it out would
        // work forwards but play silence when the offset moves backwards (§3).
        audioDelayGeneration++
    }
}
