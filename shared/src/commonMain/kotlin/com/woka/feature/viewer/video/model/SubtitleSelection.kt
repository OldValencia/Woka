package com.woka.feature.viewer.video.model

/**
 * What the subtitle overlay is currently showing (§2.2 / Step 9).
 *
 * A sidecar file has no place in the container's stream numbering, so this is a
 * sealed type rather than the nullable `Int` index it replaced — a reserved
 * negative range would have made every consumer guess which world an index came
 * from. [Embedded] carries an absolute container stream index; [External] carries
 * an absolute file path, which is also its identity in `TrackPrefs`.
 */
sealed interface SubtitleSelection {
    data object Off : SubtitleSelection
    data class Embedded(val index: Int) : SubtitleSelection
    data class External(val path: String) : SubtitleSelection

    val embeddedIndex: Int?
        get() = (this as? Embedded)?.index

    val externalPath: String?
        get() = (this as? External)?.path
}
