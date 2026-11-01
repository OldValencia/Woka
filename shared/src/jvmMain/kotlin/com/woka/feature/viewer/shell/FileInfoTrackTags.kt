package com.woka.feature.viewer.shell

import androidx.compose.runtime.Composable
import com.woka.feature.viewer.audio.AudioTags

/**
 * A track's tags in Info (AP-003): the album, year and genre the viewer no longer puts
 * under the title, beside the title and artist it does. Only the tags the file has.
 */
@Composable
internal fun TrackTagsSection(tags: AudioTags) {
    InfoSection("Track") {
        trackTagRows(tags).forEach { (label, value) -> InfoRow(label, value) }
    }
}

/** The rows, shared with the Copy text. */
internal fun trackTagRows(tags: AudioTags): List<Pair<String, String>> = listOfNotNull(
    "Title" to tags.title,
    tags.artist?.let { "Artist" to it },
    tags.album?.let { "Album" to it },
    tags.year?.let { "Year" to it },
    tags.genre?.let { "Genre" to it }
)
