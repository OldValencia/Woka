package com.woka.core.system

/** What the OS panel or a hardware media key can ask the player to do (§4). */
enum class MediaCommand { PlayPause, Play, Pause, Stop, Next, Previous }

/**
 * Metadata for the session the OS shows while a video is open, plus which
 * transport buttons it should offer. [hasNext] / [hasPrevious] are false when the
 * folder holds no other video — a live button that does nothing is worse than an
 * absent one.
 */
data class MediaSessionInfo(
    val title: String,
    val durationMs: Long,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
    /** The line under the title in the OS panel: a track's artist (AP-007). */
    val artist: String = "",
    /** A film is announced as video, a track as music; the panel lays them out differently. */
    val isVideo: Boolean = false,
    /** The cover or poster, PNG-encoded; null shows the panel's own placeholder. */
    val cover: ByteArray? = null
)

/** Transport state pushed to the OS panel. */
data class MediaPlaybackState(
    val isPlaying: Boolean,
    val positionMs: Long
)

/**
 * One platform's media session — SMTC on Windows (AP-007), macOS remote
 * commands, or nothing.
 *
 * Deliberately narrow: the OS side owns a single session for the whole process,
 * so this is a state sink, not a per-file object. [SystemMediaSession] is what
 * decides who currently owns it.
 *
 * [onCommand] is invoked on whatever thread the OS uses; marshalling it somewhere
 * safe is the caller's job, not the backend's.
 */
interface SystemMediaControls {
    var onCommand: (MediaCommand) -> Unit

    /** Registers the session with the OS. */
    fun enable()

    /** Metadata and available buttons; may be called repeatedly. */
    fun setSession(info: MediaSessionInfo)

    /** Play state and position; called on every play/pause and on a 1 s tick. */
    fun setPlayback(state: MediaPlaybackState)

    /** Unregisters the session, leaving the media keys to other applications. */
    fun disable()

    /** Releases native resources for good. */
    fun dispose()
}
