package com.woka.feature.editor.engine.video

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.seek
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * The player the canvas runs, held where **two** composables can reach it.
 *
 * `Surface` draws it and `Thumbnail` draws the same frame through a different recipe —
 * fourteen times over for a filter strip — so the player cannot be `remember`ed inside
 * either of them. The shape is `ImageEditorEngine`'s own `playback` and `measured`
 * holders: one object rather than a flag and a setter threaded separately.
 *
 * The **canvas** owns the lifetime through a `DisposableEffect`, because that is the
 * composable whose scope the decode jobs belong to; the engine only carries the holder.
 */
@Stable
internal class VideoPlayerHolder {

    var state by mutableStateOf<VideoPlayerState?>(null)
        private set

    /**
     * Idempotent per file, so a recomposition does not open a second decoder.
     *
     * **It opens paused** (ME-207), on a picture: what a crop rectangle and a markup arrow
     * have to be placed on. `isPlaying` directly and not `togglePlayPause()` — that one
     * seeks to 0 near the end and clears `reachedEnd`, and neither belongs in an open.
     *
     * **And then asks for one picture** (measured 2026-11-02, reported by the author).
     * ME-207 said the first frame is presented before the presenter parks; it is not — a
     * paused decoder decodes nothing and a paused presenter publishes nothing, so the canvas
     * stayed empty until play was pressed. A seek is the one path that decodes and
     * publishes while paused, but one requested here is **absorbed**: the decode loop
     * captures the seek generation after it starts, which `VideoPlayerDecoder` already
     * documents for the resume. So the seek is asked once the decoder has written the
     * duration, and again until a frame lands.
     */
    fun open(scope: CoroutineScope, file: File, volume: Float) {
        if (state?.file == file) return
        close()
        val player = VideoPlayerState(scope, file, initialVolume = volume).apply { isPlaying = false }
        state = player
        scope.launch { presentFirstFrame(player) }
    }

    fun close() {
        state?.dispose()
        state = null
    }
}

/** A seek every [FIRST_FRAME_RETRY_MS] until a picture is up, for at most two seconds. */
private suspend fun presentFirstFrame(player: VideoPlayerState) {
    // Polled rather than a `snapshotFlow`: the duration is written from the decoder's own
    // thread, and a flow over it only wakes when somebody sends apply notifications.
    while (player.durationMs <= 0L) delay(FIRST_FRAME_RETRY_MS)
    repeat(FIRST_FRAME_ATTEMPTS) {
        if (player.currentFrame != null || player.isPlaying) return
        player.seek(player.currentPositionMs)
        delay(FIRST_FRAME_RETRY_MS)
    }
}

private const val FIRST_FRAME_RETRY_MS = 150L
private const val FIRST_FRAME_ATTEMPTS = 14
