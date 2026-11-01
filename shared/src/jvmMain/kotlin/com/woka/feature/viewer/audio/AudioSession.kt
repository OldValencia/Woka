package com.woka.feature.viewer.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.woka.app.AppDependencies
import com.woka.core.data.MediaItem
import com.woka.feature.viewer.shell.MediaViewerPlaylist
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.setPlaybackSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The one audio player alive, and its life outside the viewer (AP-003,
 * `audio-player.md` §3.3). The viewer asks here instead of building a player, so
 * closing it — or opening the same track from Now Playing — keeps the music going.
 *
 * Every player made here is put straight into the Now Playing slot, which is what
 * stops whatever played before: the previous track, or a video in PiP.
 *
 * **The session walks the folder**, not the viewer: a track that ends moves on to the
 * next one whether or not the viewer is open, and an open viewer follows [current].
 * Repeat Off plays the folder through to its end; Folder wraps; File loops in the clock.
 */
object AudioSession {

    /** Never cancelled: a track outlives every screen, as PiP's player does. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The track playing, as state: the viewer reads it to follow the walk. */
    var current: VideoPlayerState? by mutableStateOf(null)
        private set

    /** The folder's tracks as the viewer was handed them: the walk's order. */
    private var queue: List<MediaItem> = emptyList()

    private val playlist = MediaViewerPlaylist()

    /** The player for [item] — the one already playing when it is the same file. */
    fun createOrGet(item: MediaItem.Audio, volume: Float, folder: List<MediaItem>): VideoPlayerState {
        queue = folder
        current?.takeIf { it.file.absolutePath == item.file.absolutePath }?.let { return it }
        val player = VideoPlayerState(scope, item.file, volume)
        val settings = AppDependencies.settingsRepository.settings.value
        player.repeatMode = settings.repeatMode
        player.setPlaybackSpeed(settings.playbackSpeed)
        current = player
        AppDependencies.nowPlaying.claim(AudioNowPlaying(player, scope))
        scope.launch(Dispatchers.Main) { followEnd(player) }
        return player
    }

    /**
     * Waits for [player] to end and starts what comes after it. With nothing after it,
     * the track stays where it stopped; a seek back and another end are watched again.
     * Returns once [player] is no longer the one playing.
     */
    private suspend fun followEnd(player: VideoPlayerState) {
        while (true) {
            snapshotFlow { player.reachedEnd || current !== player }.first { it }
            if (current !== player) return
            val next = nextAfter(player)
            if (next != null) {
                createOrGet(next, player.volume, queue)
                return
            }
            snapshotFlow { player.reachedEnd && current === player }.first { !it }
        }
    }

    private fun nextAfter(player: VideoPlayerState): MediaItem.Audio? {
        val settings = AppDependencies.settingsRepository.settings.value
        val path = playlist.nextPath(
            currentId = player.file.absolutePath,
            mediaItems = queue,
            shuffle = settings.shuffle,
            repeat = settings.repeatMode,
            playThrough = true
        ) ?: return null
        return queue.firstOrNull { it.file.absolutePath == path } as? MediaItem.Audio
    }

    /** Ends [player] for good and empties the slot if it is still the one held. */
    fun stop(player: VideoPlayerState) {
        if (current === player) current = null
        player.dispose()
        AppDependencies.nowPlaying.release(player.file.absolutePath)
    }

    /** One sound source: a video starting in the viewer silences the music (§3.3). */
    fun stopForVideo() {
        current?.let(::stop)
    }

    /** Whether [player] is the music, which the viewer must not dispose on leaving. */
    fun owns(player: VideoPlayerState?): Boolean = player != null && player === current
}
