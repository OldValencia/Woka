package com.woka.feature.viewer.audio

import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File

/**
 * What the viewer, Now Playing and Info say about a track (AP-003): read for display
 * only, never written — the author, 2026-11-01: no tag editing, no library.
 *
 * [subtitle] is the artist alone; the album, year and genre belong to Info (the author,
 * 2026-11-01: "artist — album" under the title read as unfamiliar). Without tags the
 * title is the file's name and the second line its folder's, so a folder of bare files
 * still reads as something.
 */
data class AudioTags(
    val title: String,
    val subtitle: String,
    val artist: String? = null,
    val album: String? = null,
    val year: String? = null,
    val genre: String? = null
) {

    companion object {

        fun read(file: File): AudioTags {
            val tags = runCatching { rawTags(file) }.getOrDefault(emptyMap())
            fun tag(vararg keys: String) = keys.firstNotNullOfOrNull { key ->
                tags[key]?.takeIf { it.isNotBlank() }
            }
            val artist = tag("artist", "album_artist")
            return AudioTags(
                title = tag("title") ?: file.nameWithoutExtension,
                subtitle = artist ?: file.absoluteFile.parentFile?.name.orEmpty(),
                artist = artist,
                album = tag("album"),
                year = tag("date", "year")?.take(4),
                genre = tag("genre")
            )
        }

        /** Container tags, then the audio stream's own: Ogg and Opus keep theirs on the stream. */
        private fun rawTags(file: File): Map<String, String> {
            val grabber = FFmpegFrameGrabber(file)
            grabber.videoStream = Int.MAX_VALUE
            try {
                grabber.start()
                val all = grabber.metadata.orEmpty() + grabber.audioMetadata.orEmpty()
                return all.mapKeys { it.key.lowercase() }
            } finally {
                try {
                    grabber.stop()
                    grabber.release()
                } catch (_: Exception) {}
            }
        }
    }
}
