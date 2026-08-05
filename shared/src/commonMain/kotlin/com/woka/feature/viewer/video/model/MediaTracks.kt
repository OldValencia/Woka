package com.woka.feature.viewer.video.model

/**
 * All tracks discovered in a container, grouped by type (§2.0). Built once on
 * open by the jvm probe; observed by the UI.
 */
data class MediaTracks(
    val video: List<MediaTrack> = emptyList(),
    val audio: List<MediaTrack> = emptyList(),
    val subtitle: List<MediaTrack> = emptyList()
) {
    val hasSubtitle: Boolean get() = subtitle.isNotEmpty()
    val hasMultipleAudio: Boolean get() = audio.size > 1
    val hasMultipleVideo: Boolean get() = video.size > 1

    companion object {
        val EMPTY = MediaTracks()
    }
}
