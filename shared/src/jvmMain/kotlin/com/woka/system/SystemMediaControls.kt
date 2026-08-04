package com.woka.system

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
    val hasPrevious: Boolean
)

/** Transport state pushed to the OS panel. */
data class MediaPlaybackState(
    val isPlaying: Boolean,
    val positionMs: Long
)

/**
 * One platform's media session — an AWT key hook on Windows, macOS remote
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
