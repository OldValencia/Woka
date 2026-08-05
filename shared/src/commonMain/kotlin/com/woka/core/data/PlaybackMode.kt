package com.woka.core.data

import kotlin.math.abs

/**
 * What happens when a video reaches its end (§6).
 *
 * [File] is the behaviour the decode loop already had as a boolean; [Folder]
 * cannot be handled in the decoder at all, because advancing to another file is
 * navigation, not decoding — see `MediaViewerPlaylist`.
 */
enum class RepeatMode {
    Off,
    File,
    Folder;

    fun next(): RepeatMode = when (this) {
        Off -> File
        File -> Folder
        Folder -> Off
    }

    val label: String
        get() = when (this) {
            Off -> "Off"
            File -> "This file"
            Folder -> "Folder"
        }
}

/** Playback speed bounds and the neutral value the slider snaps to (§6). */
object PlaybackSpeed {
    const val MIN = 0.5f
    const val NORMAL = 1.0f
    const val MAX = 2.0f

    /** Pulls values near 1× onto it, so returning to normal is a gesture. */
    fun snap(value: Float): Float {
        val clamped = value.coerceIn(MIN, MAX)
        return if (abs(clamped - NORMAL) < 0.04f) NORMAL else clamped
    }

    /** `1×`, `1.25×`, `0.5×` — trailing zeroes trimmed. */
    fun label(value: Float): String {
        val rounded = (value * 100f).toInt() / 100f
        val text = if (rounded % 1f == 0f) rounded.toInt().toString() else "$rounded"
        return "$text×"
    }
}
