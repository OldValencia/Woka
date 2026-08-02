package com.woka.ui.viewer.player

import com.woka.data.TrackRef

/** The persistable identity of this track (§2.6). */
fun MediaTrack.toRef(): TrackRef = TrackRef(language, title, codec)

/**
 * Resolves a remembered [ref] against the tracks of the file being opened (§2.6).
 *
 * Priority is the full tuple, then title, then language — deliberately **not**
 * language-first: in a season where seven of eight audio tracks are `rus`, the
 * title is the only real discriminator.
 */
fun List<MediaTrack>.matchRef(ref: TrackRef?): MediaTrack? {
    if (ref == null) return null
    val exact = firstOrNull {
        it.title == ref.title && it.language == ref.language && it.codec == ref.codec
    }
    if (exact != null) return exact
    val byTitle = ref.title?.let { title -> firstOrNull { it.title == title } }
    if (byTitle != null) return byTitle
    return ref.language?.let { language -> firstOrNull { it.language == language } }
}
