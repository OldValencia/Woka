package com.woka.feature.viewer.video.engine.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.woka.feature.viewer.video.engine.VideoPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * The preview controller for the open file, or null when there is nothing to preview.
 *
 * Keyed on the selected video track's dimensions as well as the file: the preview
 * grabbers fix their output size before `start()`, so a track swap has to rebuild
 * them rather than keep decoding at the old aspect.
 */
@Composable
internal fun rememberSeekPreview(
    videoState: VideoPlayerState?,
    durationMs: Long
): SeekPreviewController? {
    if (videoState == null || durationMs <= 0L) return null
    val track = videoState.tracks.mediaTracks.video
        .firstOrNull { it.index == videoState.tracks.selectedVideoIndex }
    val sourceWidth = track?.width ?: 0
    val sourceHeight = track?.height ?: 0
    val scope = rememberCoroutineScope()
    val holder = remember(videoState, durationMs, sourceWidth, sourceHeight) {
        SeekPreviewHolder(videoState, durationMs, sourceWidth, sourceHeight, scope)
    }
    DisposableEffect(holder) { onDispose { holder.close() } }
    return holder.controller
}

/**
 * Owns the two grabbers and the one thread they share, so the composable above stays
 * a lookup and the teardown has somewhere to live.
 */
private class SeekPreviewHolder(
    player: VideoPlayerState,
    durationMs: Long,
    sourceWidth: Int,
    sourceHeight: Int,
    scope: CoroutineScope
) {
    private val executor = Executors.newSingleThreadExecutor(::previewThread)
    private val coarse = SeekPreviewSource(player.file, true, sourceWidth, sourceHeight)
    private val exact = SeekPreviewSource(player.file, false, sourceWidth, sourceHeight)

    val controller = SeekPreviewController(
        scope = scope,
        dispatcher = executor.asCoroutineDispatcher(),
        grid = SeekPreviewGrid(durationMs),
        coarse = coarse,
        exact = exact,
        // Previews stand down whenever the picture is struggling, and outright while
        // a track swap is re-decoding back to the play head.
        canDecode = { !player.isReloadingVideo && !player.generation.isBehind }
    )

    fun close() {
        // Handed to the preview thread so it lands *after* any decode already running
        // there — releasing a grabber under a live grabImage() is a native crash, not
        // an exception. `shutdown` then lets that last task finish.
        executor.execute {
            coarse.close()
            exact.close()
        }
        executor.shutdown()
    }
}

private fun previewThread(runnable: Runnable): Thread =
    Thread(runnable, "woka-seek-preview").apply {
        isDaemon = true
        // Under every playback thread, so the OS gives the picture its quanta first.
        priority = Thread.MIN_PRIORITY
    }
