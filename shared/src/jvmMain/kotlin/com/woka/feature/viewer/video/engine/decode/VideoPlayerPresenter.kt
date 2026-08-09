package com.woka.feature.viewer.video.engine.decode

import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.audio.startAudioDecodeLoop
import com.woka.feature.viewer.video.engine.probe.HdrInfo

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.math.abs

// Consecutive drops after which the presenter re-anchors instead of staying in the
// band where nothing is late enough to rebase but everything is late enough to drop.
private const val MAX_DROPS_IN_A_ROW = 10

/** A decoded video frame tagged with the seek generation it was produced under. */
internal data class VideoFrame(
    val bitmap: ImageBitmap,
    val tsMs: Long,
    val generation: Int,
    val hdrInfo: HdrInfo? = null,
    // Last, with a default, so the end-of-stream marker in VideoPlayerSpeed.kt —
    // which carries a 1x1 placeholder that nothing retains — stays as it was.
    val id: Long = 0L
)

internal fun VideoPlayerState.startPlayback() {
    // Each queued frame owns a full-size Skia raster — 33MB at 4K — freed only
    // once the collector reaches its small Java wrapper. 60 of those is up to 2GB
    // of native memory in flight; 8 still absorbs ~330ms of decoder jitter.
    val videoChannel = Channel<VideoFrame>(capacity = 8)
    val audioChannel = Channel<ByteArray>(capacity = 500)

    startPresenterLoop(videoChannel)
    startDecoderLoop(videoChannel)
    startAudioDecodeLoop(audioChannel)
}

internal fun VideoPlayerState.startPresenterLoop(videoChannel: Channel<VideoFrame>) {
    presenterJob = scope.launch(Dispatchers.Default) {
        var startPlayTimeMs = System.nanoTime() / 1_000_000L
        var firstVideoTsMs = -1L
        var lastPauseTimeMs = 0L
        var droppedInARow = 0

        for ((bitmap, frameTsMs, frameGeneration, hdrInfo, frameId) in videoChannel) {
            // Drop frames decoded before the latest seek so they can't overwrite
            // the post-seek position/frame with stale data.
            if (frameGeneration != generation.seek) continue

            if (frameTsMs == -2L) {
                isPlaying = false
                currentPositionMs = durationMs
                reachedEnd = true // the playlist layer decides what plays next (§6)
                continue
            }
            while (!isPlaying || isSeeking) {
                if (lastPauseTimeMs == 0L) lastPauseTimeMs = System.nanoTime() / 1_000_000L
                delay(50.milliseconds)
            }
            
            if (lastPauseTimeMs != 0L) {
                startPlayTimeMs += ((System.nanoTime() / 1_000_000L) - lastPauseTimeMs)
                lastPauseTimeMs = 0L
            }

            // A video-track swap restarted the decoder under us (§2.3). The time it
            // took is not playback time, so rebase rather than bill it to these
            // frames as lateness — otherwise they get dropped to "catch up" and the
            // picture stalls until the decoder claws the gap back at ~1.07x.
            if (generation.consumePresenterRebase()) {
                firstVideoTsMs = -1L
            }

            if (firstVideoTsMs == -1L) {
                firstVideoTsMs = frameTsMs
                startPlayTimeMs = System.nanoTime() / 1_000_000L
            }

            var elapsedMs = (System.nanoTime() / 1_000_000L) - startPlayTimeMs
            // Media time is divided by the speed to get wall time: at 2x a frame
            // 1000ms into the stream is due after 500ms of real time (§6).
            var targetTimeMs = ((frameTsMs - firstVideoTsMs) / playbackSpeed).toLong()
            var sleepTime = targetTimeMs - elapsedMs

            // Discontinuity detection: if the frame is wildly out of sync (> 1000ms),
            // it means a seek occurred or the channel contained stale frames.
            if (abs(sleepTime) > 1000) {
                firstVideoTsMs = frameTsMs
                startPlayTimeMs = System.nanoTime() / 1_000_000L
                elapsedMs = 0
                targetTimeMs = 0
                sleepTime = 0
            }

            if (sleepTime > 0) {
                delay(sleepTime.milliseconds)
            } else if (sleepTime < -50) {
                // A stall — a long GC, a burst of slow conversions — leaves the
                // baseline behind by more than the drop threshold but less than the
                // 1s discontinuity one. That dead band drops every frame forever and
                // the picture never comes back, so a run of drops rebases too.
                if (++droppedInARow >= MAX_DROPS_IN_A_ROW) firstVideoTsMs = -1L
                // Frame is more than 50ms late, drop it to catch up with audio
                if (frameGeneration != generation.seek) continue
                if (frameTsMs >= currentPositionMs || abs(currentPositionMs - frameTsMs) > 1000) {
                    currentPositionMs = frameTsMs
                }
                continue
            }

            // A seek may have completed while we were parked or sleeping above.
            // If so this frame is stale — dropping it here stops it from dragging
            // the play head back to the pre-seek position (visible only while
            // playing, when the presenter is mid-sleep rather than parked).
            if (frameGeneration != generation.seek) continue

            if (frameTsMs >= currentPositionMs || abs(currentPositionMs - frameTsMs) > 1000) {
                currentPositionMs = frameTsMs
            }

            droppedInARow = 0
            currentFrame = bitmap
            currentFrameHdrInfo = hdrInfo
            currentFrameId = frameId
        }
    }
}
