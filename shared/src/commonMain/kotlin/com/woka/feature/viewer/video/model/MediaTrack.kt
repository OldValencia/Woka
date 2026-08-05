package com.woka.feature.viewer.video.model

/** Media stream category within a container. */
enum class TrackType { Video, Audio, Subtitle }

/**
 * A single decodable stream inside a media container (§2.0). Pure Kotlin so the
 * jvm probe and the common UI share the same model.
 *
 * Matching and persistence use [identity] — the (type, language, title, codec)
 * tuple — never [index], which differs between files (see §2.0 / §2.6).
 */
data class MediaTrack(
    val index: Int,
    val number: Int,
    val type: TrackType,
    val codec: String,
    val language: String? = null,
    val title: String? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    // Video-only.
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Double? = null,
    val bitRate: Long? = null,
    // Audio-only.
    val channels: Int? = null,
    val channelLayout: String? = null,
    // Subtitle-only.
    val isTextBased: Boolean? = null
) {
    /** Display label: title, else language, else a 1-based per-type ordinal. */
    val label: String get() = title ?: language ?: "Track $number"

    /** Stable cross-file identity for matching and persistence (§2.0 / §2.6). */
    val identity: TrackIdentity get() = TrackIdentity(type, language, title, codec)
}

/** Stable identity tuple used to match a track across different files. */
data class TrackIdentity(
    val type: TrackType,
    val language: String?,
    val title: String?,
    val codec: String
)
