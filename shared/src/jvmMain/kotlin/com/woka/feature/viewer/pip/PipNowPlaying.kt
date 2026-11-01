package com.woka.feature.viewer.pip

import androidx.compose.ui.graphics.ImageBitmap
import com.woka.app.shell.nowplaying.NowPlayingSource
import com.woka.core.data.MediaItem
import com.woka.core.utils.generateVideoThumbnail
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.togglePlayPause

/**
 * A video in PiP as the app's Now Playing source (AP-004): its poster, its name,
 * and the PiP window as the place [reveal] brings forward.
 */
internal class PipNowPlaying(
    private val item: MediaItem,
    private val player: VideoPlayerState
) : NowPlayingSource {

    override val path: String = item.file.absolutePath
    override val title: String = item.file.name
    override val subtitle: String = "Picture in Picture"

    override val isPlaying: Boolean get() = player.isPlaying

    override val progress: Float
        get() {
            val length = player.durationMs
            if (length <= 0L) return 0f
            return (player.currentPositionMs.toFloat() / length).coerceIn(0f, 1f)
        }

    override suspend fun cover(): ImageBitmap? = generateVideoThumbnail(item.file)

    override fun togglePlayPause() = player.togglePlayPause()

    /**
     * Only while PiP still plays **this** player. `openPip` on another file has already
     * disposed it and installed the newcomer by the time `NowPlaying.claim` stops the
     * old source; closing PiP then would close the film just opened.
     */
    override fun stop() {
        if (PipManager.videoState === player) PipManager.closePip()
    }

    override fun reveal() = PipManager.requestFront()
}
