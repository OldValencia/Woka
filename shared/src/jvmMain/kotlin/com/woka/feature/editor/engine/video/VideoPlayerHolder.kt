package com.woka.feature.editor.engine.video

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.woka.feature.viewer.video.engine.VideoPlayerState
import kotlinx.coroutines.CoroutineScope
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
     * **It opens paused** (ME-207). `VideoPlayerState.init` starts the decode loop, so
     * the first frame is presented and the presenter then parks: a picture on screen,
     * standing, which is what a crop rectangle and a markup arrow have to be placed on.
     * `isPlaying` directly and not `togglePlayPause()` — that one seeks to 0 near the end
     * and clears `reachedEnd`, and neither belongs in an open.
     */
    fun open(scope: CoroutineScope, file: File, volume: Float) {
        if (state?.file == file) return
        close()
        state = VideoPlayerState(scope, file, initialVolume = volume).apply { isPlaying = false }
    }

    fun close() {
        state?.dispose()
        state = null
    }
}
