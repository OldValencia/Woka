package com.woka.feature.viewer.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.ImageBitmap
import com.woka.app.AppDependencies
import com.woka.app.navigation.Screen
import com.woka.app.shell.nowplaying.NowPlayingSource
import com.woka.core.data.MediaItem
import com.woka.feature.viewer.shell.MediaThumbnails
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.togglePlayPause
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A track as the app's Now Playing source (AP-003): its cover, its tags, and the
 * viewer open on it as the place [reveal] goes back to.
 *
 * The tags start as the file's name and are replaced once read, off the main
 * thread — the slot is claimed during composition and must not wait on a file.
 */
internal class AudioNowPlaying(
    private val player: VideoPlayerState,
    scope: CoroutineScope
) : NowPlayingSource {

    override val path: String = player.file.absolutePath

    override var title: String by mutableStateOf(player.file.nameWithoutExtension)
        private set

    override var subtitle: String by mutableStateOf(player.file.absoluteFile.parentFile?.name.orEmpty())
        private set

    init {
        scope.launch(Dispatchers.IO) {
            val tags = AudioTags.read(player.file)
            Snapshot.withMutableSnapshot {
                title = tags.title
                subtitle = tags.subtitle
            }
        }
    }

    override val isPlaying: Boolean get() = player.isPlaying

    override val progress: Float
        get() {
            val length = player.durationMs
            if (length <= 0L) return 0f
            return (player.currentPositionMs.toFloat() / length).coerceIn(0f, 1f)
        }

    override suspend fun cover(): ImageBitmap? = MediaThumbnails.loadThumbnail(MediaItem.Audio(player.file))

    override fun togglePlayPause() = player.togglePlayPause()

    override fun stop() = AudioSession.stop(player)

    override fun reveal() {
        val parent = player.file.absoluteFile.parent ?: return
        AppDependencies.router.navigateTo(
            Screen.Folder(path = parent, openViewerForId = path, nonce = System.nanoTime())
        )
    }
}
