package com.woka.data

import kotlinx.serialization.Serializable

/**
 * A remembered track, stored by its stable identity tuple (§2.0 / §2.6) — never
 * by the container stream index, which differs between files of the same series.
 */
@Serializable
data class TrackRef(
    val language: String? = null,
    val title: String? = null,
    val codec: String = ""
)

/**
 * The track choices remembered for one file, or — minus [video] — for one folder
 * (§2.6). Records are keyed by absolute path: the required cleanup on unlink is a
 * path-prefix sweep, which a content-keyed map cannot answer.
 *
 * **A field is null when nothing was chosen for it *in this file*** (Step 15).
 * The per-file record used to be a full snapshot of whatever happened to be
 * playing, so a single unrelated action — hiding a sidecar, say — froze the
 * audio and subtitle inherited from the folder into the file as if they had been
 * picked there, and later folder-level changes could never reach it again.
 * Untouched dimensions are now left empty and resolve from the folder layer.
 *
 * [subtitleOff] is therefore three-valued: `true` = the user turned subs off here,
 * `false` = they turned some on, `null` = they never said.
 */
@Serializable
data class TrackPrefs(
    val audio: TrackRef? = null,
    val subtitle: TrackRef? = null,
    val subtitleOff: Boolean? = null,
    val video: TrackRef? = null,
    // External sidecar subtitles (Step 9). The path identifies one specific file,
    // so the folder layer strips it and keeps only the language — the next episode
    // has its own sidecar, and what carries over is which language was wanted.
    val subtitleExternal: String? = null,
    val subtitleExternalLanguage: String? = null,
    // Files added through the picker. The automatic scan cannot find these again —
    // they may live anywhere and be named anything — so without this list a
    // hand-picked subtitle would not survive re-opening the video.
    val subtitleExternalAdded: List<String> = emptyList(),
    // Sidecars dismissed from the list. The list is built by scanning the folder,
    // not from this file, so hiding needs a record of its own — otherwise the next
    // open finds the file again and puts it straight back. Re-adding it through
    // the picker clears the entry.
    val subtitleExternalHidden: List<String> = emptyList(),
    // Sync offsets (§3), per-file only: they describe one encode's timing, not a
    // series' preference, so the folder layer strips them like it strips [video].
    val subtitleDelayMs: Long = 0L,
    val audioDelayMs: Long = 0L
) {
    /**
     * Whether this record says anything at all about subtitles. The four subtitle
     * fields are one decision, so they are tested together: a record that only
     * hides a sidecar has not chosen a subtitle track.
     */
    val hasSubtitleChoice: Boolean
        get() = subtitleOff != null ||
            subtitle != null ||
            subtitleExternal != null ||
            subtitleExternalLanguage != null
}
