package com.woka.app.shell.nowplaying

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap

/**
 * One thing that plays and outlives the screen it started on — today a video in PiP,
 * later an audio file (`features/pending/audio-player/` §3.3).
 *
 * [isPlaying] and [progress] read snapshot state, so a composable that reads them
 * recomposes as the player moves. [progress] is 0..1, and 0 while the length is unknown.
 */
interface NowPlayingSource {
    /** Absolute path of the file: the slot's identity for [NowPlaying.claim] and [NowPlaying.release]. */
    val path: String
    val title: String
    val subtitle: String
    val isPlaying: Boolean
    val progress: Float

    suspend fun cover(): ImageBitmap?

    fun togglePlayPause()

    /** Stops playback for good; the source releases the slot itself on the way out. */
    fun stop()

    /** Brings the playing thing into view: the PiP window to the front, or the viewer open on it. */
    fun reveal()

    /**
     * Whether the OS media keys may skip, and the skip itself (AP-007). A video in PiP is
     * one file and offers neither; a track walks its folder.
     */
    val hasNext: Boolean get() = false
    val hasPrevious: Boolean get() = false

    fun next() {}

    fun previous() {}
}

/**
 * The app's one sound source (AP-004). Shell state that two features read — the
 * sidebar draws it, the viewer's PiP fills it — so it lives in `app/shell` beside
 * `EditorPresence`, for the reason that type gives.
 *
 * **One, never a stack.** The author, 2026-11-01: "это не миксер". A new source stops
 * the one held; nothing resumes when the newcomer ends.
 */
@Stable
class NowPlaying {

    var source: NowPlayingSource? by mutableStateOf(null)
        private set

    /**
     * The file the viewer has open, or null with no viewer. The bubble hides while it
     * is the slot's own file: the thing it points at is already on screen (AP-006).
     */
    var shownInViewer: String? by mutableStateOf(null)

    /** The sidebar row's cover, in root coordinates: where the bubble's flight starts or lands (AP-008). */
    var rowCover: Rect? by mutableStateOf(null)

    /**
     * Holds [next], stopping the source held before it. The same path is the same
     * playback handed on — PiP opened again on its own file — and is not stopped.
     */
    fun claim(next: NowPlayingSource) {
        val held = source
        if (held != null && held.path != next.path) held.stop()
        source = next
    }

    /** Empties the slot, but only if it still holds [path]: a late release must not clear a newcomer. */
    fun release(path: String) {
        if (source?.path == path) source = null
    }
}
