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
 * [subtitleOff] carries the explicit Off state (§2.2), so "the user turned subs
 * off here" is distinguishable from "no record yet".
 */
@Serializable
data class TrackPrefs(
    val audio: TrackRef? = null,
    val subtitle: TrackRef? = null,
    val subtitleOff: Boolean = true,
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
)
