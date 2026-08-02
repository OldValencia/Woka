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
    val video: TrackRef? = null
)
